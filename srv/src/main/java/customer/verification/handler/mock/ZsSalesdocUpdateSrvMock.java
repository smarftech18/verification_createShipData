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
 * ZS_SALESDOC_UPDATE_SRV のモックハンドラ。
 *
 * <p>updateS4Status アクションで呼ばれる以下の2メソッドをインターセプトする。
 * <ul>
 *   <li>{@code updateS4DocStatus} → {@code UpdateSalesDocStatus} への INSERT (CREATE)</li>
 *   <li>{@code insertS4DocLog}    → {@code InsertSalesDocLog} への INSERT (CREATE)</li>
 * </ul>
 *
 * <p>【UpdateSalesDocStatus の動作モード（パターンB）】
 * <ul>
 *   <li>1回目の呼び出し: スルー（実際の RemoteService が動作する）</li>
 *   <li>2回目以降: HTTP 503 をスロー → {@code updateS4DocStatus} の catch へ伝播</li>
 * </ul>
 * → 「1件目は成功・2件目は失敗」という部分成功シナリオのテストに使用する。
 *
 * <p>【InsertSalesDocLog の動作モード（パターンB）】
 * <ul>
 *   <li>1回目の呼び出し: スルー（実際の RemoteService が動作する）</li>
 *   <li>2回目以降: HTTP 503 をスロー → {@code insertS4DocLog} の warn ログのみ（業務継続）</li>
 * </ul>
 * → ログ登録失敗は業務エラーにならないが、例外経路を通るテストに使用できる。
 *
 * <p>テストの {@code @BeforeEach} で {@link #reset()} を呼び出すこと。
 */
@Component
@Profile("mocked")
@ServiceName("ZS_SALESDOC_UPDATE_SRV")
public class ZsSalesdocUpdateSrvMock implements EventHandler {

    // ErrorStatuses に SERVICE_UNAVAILABLE (503) が存在しないため ErrorStatus をインライン実装する
    private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
        @Override public int getHttpStatus()    { return 503; }
        @Override public String getCodeString() { return "SERVICE_UNAVAILABLE"; }
    };

    /** UpdateSalesDocStatus 呼び出し回数 */
    private final AtomicInteger updateCallCount = new AtomicInteger(0);

    /** InsertSalesDocLog 呼び出し回数 */
    private final AtomicInteger logCallCount = new AtomicInteger(0);

    /**
     * UpdateSalesDocStatus CREATE をインターセプトする。
     *
     * <p>updateS4DocStatus が使う INSERT (POST) 呼び出しをモック化する。
     * 例外は {@code DocumentProcessingException} へラップされ errorCount に計上される。
     */
    @Before(event = CqnService.EVENT_CREATE, entity = "UpdateSalesDocStatus")
    public void beforeUpdateStatus() {
        int count = updateCallCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目はスルー → 実際のRemoteServiceが動く
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZS_SALESDOC_UPDATE_SRV (UpdateSalesDocStatus) への接続に失敗しました（モック）"
        );
    }

    /**
     * InsertSalesDocLog CREATE をインターセプトする。
     *
     * <p>insertS4DocLog が使う INSERT (POST) 呼び出しをモック化する。
     * 例外は log.warn のみ処理され、業務フローは継続する。
     */
    @Before(event = CqnService.EVENT_CREATE, entity = "InsertSalesDocLog")
    public void beforeInsertLog() {
        int count = logCallCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目はスルー → 実際のRemoteServiceが動く
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZS_SALESDOC_UPDATE_SRV (InsertSalesDocLog) への接続に失敗しました（モック）"
        );
    }

    /**
     * テスト間で呼び出し回数をリセットする。
     *
     * <p>使用例:
     * <pre>{@code
     * @BeforeEach
     * void setUp(@Autowired ZsSalesdocUpdateSrvMock mock) {
     *     mock.reset();
     * }
     * }</pre>
     */
    public void reset() {
        updateCallCount.set(0);
        logCallCount.set(0);
    }
}
