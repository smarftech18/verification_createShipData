package customer.verification.handler.mock;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ZC_SALESDOCUMENT_SERVICE のモックハンドラ。
 *
 * <p>createSalesDocuments アクションで呼ばれる {@code fetchZcSalesDocuments} の
 * S4 READ 呼び出しをインターセプトする。
 *
 * <p>【動作モード】
 * <ul>
 *   <li>1回目: スルー（実際の RemoteService が動作する）</li>
 *   <li>2回目以降: HTTP 503 をスロー → fetchZcSalesDocuments の catch へ伝播</li>
 * </ul>
 *
 * <p>テストの {@code @BeforeEach} で {@link #reset()} を呼び出すこと。
 */
@Component
@Profile("mocked")
@ServiceName("ZC_SALESDOCUMENT_SERVICE")
public class ZcSalesDocumentMock implements EventHandler {

    // ErrorStatuses に SERVICE_UNAVAILABLE (503) が存在しないため ErrorStatus をインライン実装する
    private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
        @Override public int getHttpStatus()    { return 503; }
        @Override public String getCodeString() { return "SERVICE_UNAVAILABLE"; }
    };

    private final AtomicInteger callCount = new AtomicInteger(0);

    /**
     * ZcSalesDocument READ をインターセプトする。
     *
     * <p>{@code @Before} フェーズで例外をスローするため、
     * {@code @On}（実際の RemoteService 呼び出し）には到達しない。
     */
    @Before(event = CqnService.EVENT_READ, entity = "ZcSalesDocument")
    public void beforeRead() {
        int count = callCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目はスルー → 実際のRemoteServiceが動く
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZC_SALESDOCUMENT_SERVICE への接続に失敗しました（モック）"
        );
    }

    /**
     * テスト間で呼び出し回数をリセットする。
     *
     * <p>使用例:
     * <pre>{@code
     * @BeforeEach
     * void setUp(@Autowired ZcSalesDocumentMock mock) {
     *     mock.reset();
     * }
     * }</pre>
     */
    public void reset() {
        callCount.set(0);
    }
}
