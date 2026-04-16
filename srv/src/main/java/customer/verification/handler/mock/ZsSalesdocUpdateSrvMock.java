package customer.verification.handler.mock;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
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
 * <p>【@Before と @On を両方定義する理由】
 * {@code @ServiceName} を持つBeanを登録すると、CAP Java は組み込みの @On モックを
 * 無効化する。@On が存在しないと "No ON handler completed the processing" になるため、
 * 正常系（1回目）の @On を自クラスで実装する必要がある。
 *
 * <p>【UpdateSalesDocStatus の動作フロー（パターンB）】
 * <pre>
 * 1回目: @Before (count=1 → return) → @On (正常完了)
 * 2回目: @Before (count=2 → 503 throw) → updateS4DocStatus catch → errorCount++
 * </pre>
 * → 「1件目は成功・2件目は失敗」の部分成功シナリオをテストできる。
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

    // ---------------------------------------------------------------
    // UpdateSalesDocStatus
    // ---------------------------------------------------------------

    /**
     * 2回目以降の呼び出しで 503 をスローする。
     *
     * <p>例外は {@code updateS4DocStatus} の catch(ServiceException) → DocumentProcessingException
     * にラップされて伝播し、明細ループの errorCount に計上される。
     */
    @Before(event = CqnService.EVENT_CREATE, entity = "UpdateSalesDocStatus")
    public void beforeUpdateStatus() {
        int count = updateCallCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目: @On へ進む
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZS_SALESDOC_UPDATE_SRV (UpdateSalesDocStatus) への接続に失敗しました（モック）"
        );
    }

    /**
     * INSERT を正常完了させる（1回目のみ到達）。
     *
     * <p>呼び出し元（{@code updateS4DocStatus}）は戻り値を使用しないため、
     * 結果を設定せず正常返却するだけでよい。
     */
    @On(event = CqnService.EVENT_CREATE, entity = "UpdateSalesDocStatus")
    public void onCreateUpdateStatus(EventContext ctx) {
        // 正常完了（呼び出し元は戻り値を使用しない）
    }

    // ---------------------------------------------------------------
    // InsertSalesDocLog
    // ---------------------------------------------------------------

    /**
     * 2回目以降の呼び出しで 503 をスローする。
     *
     * <p>例外は {@code insertS4DocLog} の catch(Exception) で warn ログのみ出力され
     * 業務フローは継続する。
     */
    @Before(event = CqnService.EVENT_CREATE, entity = "InsertSalesDocLog")
    public void beforeInsertLog() {
        int count = logCallCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目: @On へ進む
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZS_SALESDOC_UPDATE_SRV (InsertSalesDocLog) への接続に失敗しました（モック）"
        );
    }

    /**
     * INSERT を正常完了させる（1回目のみ到達）。
     */
    @On(event = CqnService.EVENT_CREATE, entity = "InsertSalesDocLog")
    public void onCreateInsertLog(EventContext ctx) {
        // 正常完了（ログ登録失敗は業務影響なし）
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
