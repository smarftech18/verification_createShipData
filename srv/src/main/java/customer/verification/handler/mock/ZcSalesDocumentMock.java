package customer.verification.handler.mock;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ZC_SALESDOCUMENT_SERVICE のモックハンドラ。
 *
 * <p>createSalesDocuments アクションで呼ばれる {@code fetchZcSalesDocuments} の
 * S4 READ 呼び出しをインターセプトする。
 *
 * <p>【@Before と @On を両方定義する理由】
 * {@code @ServiceName} を持つBeanを登録すると、CAP Java は組み込みの @On モック
 * （--with-mocks で H2 に作成されたテーブルを返すハンドラ）を無効化する。
 * そのため、正常系（1回目）の @On も自クラスで実装する必要がある。
 *
 * <p>【動作フロー】
 * <pre>
 * 1回目: @Before (count=1 → return) → @On (H2 からデータ取得して返す)
 * 2回目: @Before (count=2 → 503 throw) → @On には到達しない
 *         └─ fetchZcSalesDocuments catch(ServiceException) → DocumentProcessingException
 *            └─ onCreateSalesDocuments catch(DocumentProcessingException) → {success:false}
 * </pre>
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

    @Autowired
    private PersistenceService db;

    private final AtomicInteger callCount = new AtomicInteger(0);

    /**
     * 2回目以降の呼び出しで 503 をスローする。
     *
     * <p>1回目は return して @On フェーズへ進む。
     * 2回目以降は ServiceException をスローして @On には到達させない。
     */
    @Before(event = CqnService.EVENT_READ, entity = "ZcSalesDocument")
    public void beforeRead() {
        int count = callCount.incrementAndGet();
        if (count == 1) {
            return; // 1回目: @On へ進む
        }
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZC_SALESDOCUMENT_SERVICE への接続に失敗しました（モック）"
        );
    }

    /**
     * H2 のモックテーブルからデータを取得して返す（1回目のみ到達）。
     *
     * <p>--with-mocks ビルドにより H2 に ZC_SALESDOCUMENT_SERVICE_ZcSalesDocument
     * テーブルが作成されており、CSV のデータが読み込まれている。
     * PersistenceService でそのまま実行することで組み込みモックと同等の動作をする。
     */
    @On(event = CqnService.EVENT_READ, entity = "ZcSalesDocument")
    public void onRead(CdsReadEventContext ctx) {
        ctx.setResult(db.run(ctx.getCqn()));
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
