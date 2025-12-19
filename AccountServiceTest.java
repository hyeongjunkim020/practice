package com.example.banksystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AccountServiceConcurrencyTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private Long senderId;
    private Long receiverId;

    @BeforeEach
    void setUp() {
        // 테스트 시작 전 데이터 세팅
        // 형준: 1000원, 지호: 1000원
        Account sender = new Account(null, "110-234-567890", "김스프링", new BigDecimal("1000")); // 형준
        Account receiver = new Account(null, "220-987-654321", "이부트", new BigDecimal("1000")); // 지호

        senderId = accountRepository.save(sender).getId();
        receiverId = accountRepository.save(receiver).getId();
    }


    @Test
    @DisplayName("동시성 테스트: 100원씩 10번 동시에 이체하면 원자성이 깨져서 잔액이 맞지 않는다.")
    void concurrentTransferTest() throws InterruptedException {
        // Given
        int threadCount = 10; // 10번의 동시 요청
        BigDecimal transferAmount = new BigDecimal("100"); // 1회 이체 금액

        // 멀티스레드 환경 구성을 위한 ExecutorService (비동기 작업 실행)
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // 모든 스레드가 작업을 마칠 때까지 대기하기 위한 Latch
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        System.out.println("--- 동시 이체 요청 시작 ---");
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    accountService.transferMoney(senderId, receiverId, transferAmount);
                } finally {
                    latch.countDown(); // 작업이 끝나면 카운트 감소
                }
            });
        }

        latch.await(); // 모든 스레드(10개)가 종료될 때까지 대기
        System.out.println("--- 동시 이체 요청 종료 ---");

        // Then
        // 1. DB에서 최종 결과 조회
        Account finalSender = accountRepository.findById(senderId).orElseThrow();
        Account finalReceiver = accountRepository.findById(receiverId).orElseThrow();

        System.out.println("최종 형준 잔액: " + finalSender.getBalance());
        System.out.println("최종 지호 잔액: " + finalReceiver.getBalance());
        System.out.println("총 잔액 합계: " + finalSender.getBalance().add(finalReceiver.getBalance()));

        // 2. 판단 기준 검증
        // 기대 결과: 형준이는 1000원에서 100원씩 10번 뺐으니 0원이 되어야 함.
        // 실제 결과: 동시성 이슈로 인해 0원이 되지 않음 (테스트가 실패하거나, 이슈를 증명하기 위해 NotEquals 사용)

        // [검증 1] 데이터 무결성이 깨졌음을 확인 (이 코드는 통과해야 함 -> 즉, 0원이 아님)
        assertThat(finalSender.getBalance()).isNotEqualByComparingTo(BigDecimal.ZERO);

        // [검증 2] 총 잔액은 변함이 없어야 하는데, 로직상 누락이 발생하면 총액도 안 맞을 수 있음
        // (현재 로직은 빼고 더하기를 각각 수행하므로, setBalance 시점에 따라 총액 불일치 가능성 높음)
    }
}