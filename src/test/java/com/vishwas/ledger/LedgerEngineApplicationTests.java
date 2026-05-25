package com.vishwas.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vishwas.ledger.dto.AccountCreateRequest;
import com.vishwas.ledger.dto.TransferRequest;
import com.vishwas.ledger.entity.LedgerEntry;
import com.vishwas.ledger.repository.AccountRepository;
import com.vishwas.ledger.repository.LedgerEntryRepository;
import com.vishwas.ledger.repository.TransactionRepository;
import com.vishwas.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerEngineApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ObjectMapper objectMapper;

    // Mocking Redis StringRedisTemplate so tests are 100% portable
    // and run seamlessly without requiring a local Redis server instance.
    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        // Mock standard Spring Data Redis operations
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertNotNull(mockMvc);
    }

    @Test
    void createAccount_Success() throws Exception {
        AccountCreateRequest request = new AccountCreateRequest("User_A", new BigDecimal("500.00"));

        mockMvc.perform(post("/api/ledger/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("User_A"))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    void transferFunds_DoubleEntryIntegrity_Success() throws Exception {
        // Create initial wallets
        ledgerService.createAccount(new AccountCreateRequest("SenderWallet", new BigDecimal("1000.0000")));
        ledgerService.createAccount(new AccountCreateRequest("ReceiverWallet", new BigDecimal("0.0000")));

        TransferRequest request = new TransferRequest("SenderWallet", "ReceiverWallet", new BigDecimal("300.0000"), "Monthly Rent");

        // Mock redis key is new
        when(valueOperations.setIfAbsent(eq("idemp:key-rent"), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);

        mockMvc.perform(post("/api/ledger/transfer")
                        .header("Idempotency-Key", "key-rent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value("key-rent"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        // 1. Verify individual balances
        assertEquals(0, new BigDecimal("700.0000").compareTo(ledgerService.getAccount("SenderWallet").getBalance()));
        assertEquals(0, new BigDecimal("300.0000").compareTo(ledgerService.getAccount("ReceiverWallet").getBalance()));

        // 2. Verify double-entry ledger lines exist
        List<LedgerEntry> senderHistory = ledgerService.getAccountHistory("SenderWallet");
        assertEquals(1, senderHistory.size());
        assertEquals(LedgerEntry.EntryType.DEBIT, senderHistory.get(0).getType());

        List<LedgerEntry> receiverHistory = ledgerService.getAccountHistory("ReceiverWallet");
        assertEquals(1, receiverHistory.size());
        assertEquals(LedgerEntry.EntryType.CREDIT, receiverHistory.get(0).getType());

        // 3. Mathematical Invariant Verification (Perfect Double-Entry Balance)
        BigDecimal sumOfAllBalances = ledgerService.getAccount("SenderWallet").getBalance()
                .add(ledgerService.getAccount("ReceiverWallet").getBalance());
        assertEquals(0, new BigDecimal("1000.0000").compareTo(sumOfAllBalances));
    }

    @Test
    void transferFunds_Overdraft_Failure() throws Exception {
        ledgerService.createAccount(new AccountCreateRequest("SourceAccount", new BigDecimal("100.0000")));
        ledgerService.createAccount(new AccountCreateRequest("DestAccount", new BigDecimal("0.0000")));

        TransferRequest request = new TransferRequest("SourceAccount", "DestAccount", new BigDecimal("150.0000"), "Overdraft attempt");

        when(valueOperations.setIfAbsent(eq("idemp:key-overdraft"), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);

        mockMvc.perform(post("/api/ledger/transfer")
                        .header("Idempotency-Key", "key-overdraft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Insufficient funds")));
    }

    @Test
    void transferFunds_IdempotencyKeyReplay_CachedResponse() throws Exception {
        TransferRequest request = new TransferRequest("SenderWallet", "ReceiverWallet", new BigDecimal("300.00"), "Rent");

        // Mock redis key already exists and has a cached completed response
        String cachedResponse = "COMPLETED:200:{\"referenceId\":\"key-cached\",\"sourceAccountName\":\"SenderWallet\",\"destinationAccountName\":\"ReceiverWallet\",\"amount\":300.00,\"status\":\"SUCCESS\",\"timestamp\":\"2026-05-25T10:00:00\"}";
        when(valueOperations.setIfAbsent(eq("idemp:key-cached"), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(eq("idemp:key-cached"))).thenReturn(cachedResponse);

        mockMvc.perform(post("/api/ledger/transfer")
                        .header("Idempotency-Key", "key-cached")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value("key-cached"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void transferFunds_HighConcurrency_OptimisticLockRetries() throws Exception {
        // Create accounts
        ledgerService.createAccount(new AccountCreateRequest("AliceWallet", new BigDecimal("1000.0000")));
        ledgerService.createAccount(new AccountCreateRequest("BobWallet", new BigDecimal("0.0000")));

        // Spawn 10 concurrent transfers of 100 each from Alice to Bob.
        // Under high concurrency, threads will trigger Optimistic Lock conflicts.
        // Because of the service's retry loop, they should all eventually succeed cleanly.
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String referenceId = "idemp-key-concurrency-" + i;
            executor.submit(() -> {
                try {
                    latch.await(); // Hold all threads to launch simultaneously
                    TransferRequest request = new TransferRequest("AliceWallet", "BobWallet", new BigDecimal("100.0000"), "Concurrent Transfer");
                    ledgerService.transferFunds(request, referenceId);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Launch all threads simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert balances are mathematically correct
        assertEquals(0, new BigDecimal("0.0000").compareTo(ledgerService.getAccount("AliceWallet").getBalance()));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(ledgerService.getAccount("BobWallet").getBalance()));
    }
}
