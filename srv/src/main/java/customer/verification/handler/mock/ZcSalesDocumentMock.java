package customer.verification.handler.mock;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import customer.verification.SalesDocumentHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * ZC_SALESDOCUMENT_SERVICE のモックハンドラ（Pattern A: 常に例外）。
 *
 * <p>
 * createSalesDocuments アクションで呼ばれる {@code fetchZcSalesDocuments} の
 * S4 READ 呼び出しをインターセプトする。
 *
 * <p>
 * 【Pattern A を採用する理由】
 * Pattern B（1回目スルー・2回目例外）は @On で H2 からデータを返す実装が必要であり、
 * CAP Java のバージョン依存が発生しやすい。
 * catch 挙動の確認には "常に例外" の Pattern A で十分。
 * 正常系（成功パス）は mocked プロファイルなしで確認する。
 *
 * <p>
 * 【@On が不要な理由】
 * 
 * @Before が例外をスローすると CAP はそこでディスパッチを中断し、
 *         例外をそのまま s4Service.run() の呼び出し元へ伝播する。
 * @On フェーズには到達しないため @On ハンドラは不要。
 *
 *     <p>
 *     【期待する挙動】
 * 
 *     <pre>
 * @Before → ServiceException(503) throw
 *   → s4Service.run() に例外伝播
 *     → fetchZcSalesDocuments catch(ServiceException)
 *       → DocumentProcessingException throw
 *         → onCreateSalesDocuments catch(DocumentProcessingException)
 *           → {success: false, message: "ZcSalesDocument 取得エラー..."}
 *     </pre>
 */
@Component
@Profile("mocked")
@ServiceName("ZC_SALESDOCUMENT_SERVICE")
public class ZcSalesDocumentMock implements EventHandler {
    private static final Logger log = LoggerFactory.getLogger(SalesDocumentHandler.class);

    // ErrorStatuses に SERVICE_UNAVAILABLE (503) が存在しないため ErrorStatus をインライン実装する
    private static final ErrorStatus SERVICE_UNAVAILABLE = new ErrorStatus() {
        @Override
        public int getHttpStatus() {
            return 503;
        }

        @Override
        public String getCodeString() {
            return "SERVICE_UNAVAILABLE";
        }
    };

    /**
     * ZcSalesDocument READ を常に 503 でブロックする。
     *
     * <p>
     * @Before でスローした例外は @On より先に伝播するため @On は不要。
     * このメソッドにブレイクポイントを置くことで mock が動作しているか確認できる。
     */
    @On(event = CqnService.EVENT_READ, entity = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument")
    public void beforeRead() {
        log.info("mooooocked throw");
        System.out.println("mooooocked throw2");
        throw new ServiceException(
                SERVICE_UNAVAILABLE,
                "ZC_SALESDOCUMENT_SERVICE への接続に失敗しました（モック）");
    }
}
