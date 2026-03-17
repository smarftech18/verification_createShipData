package customer.verification;

import cds.gen.com.example.bp.SalesDocDetail;
import cds.gen.com.example.bp.SalesDocHeader;
import cds.gen.com.example.bp.SalesDocItem;

import com.sap.cds.Row;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesDocumentHandler のトランザクション分離テスト。
 *
 * <p>【テスト方針】
 * REQUIRES_NEW による伝票単位トランザクション分離を検証する。
 * トランザクション動作は実際の機構でしか確認できないため、H2 実DBを使用する。
 *
 * <ul>
 *   <li>CdsRuntime: @MockBean（外部S4への接続を防ぐ）</li>
 *   <li>PersistenceService: 実H2（コミット/ロールバックを実際に確認）</li>
 *   <li>TransactionTemplate: 実トランザクション（REQUIRES_NEW の分離を確認）</li>
 * </ul>
 *
 * <p>【失敗のトリガー】
 * テスト対象データと同じキーを事前にDBへ投入する。
 * saveDocuments() が INSERT しようとした時に主キー重複エラーが発生し、
 * そのトランザクションがロールバックされる。
 * → 実際の本番障害と同じメカニズムで検証できる。
 *
 * <p>【注意: @Transactional をテストクラスに付与しない理由】
 * REQUIRES_NEW で開始した内側トランザクションは外側と独立してコミットされる。
 * テストクラスに @Transactional を付与すると、テスト終了時のロールバックが
 * 内側トランザクションのコミット済みデータに影響しない。
 * データが残るため @AfterEach で明示的に削除する。
 */
@SpringBootTest
@DisplayName("SalesDocumentHandler トランザクション分離テスト")
class SalesDocumentHandlerTransactionTest {

    // ---------------------------------------------------------------
    // エンティティパス（ハンドラ本体と合わせる）
    // ---------------------------------------------------------------
    private static final String HEADER_ENTITY = "com.example.bp.SalesDocHeader";
    private static final String ITEM_ENTITY   = "com.example.bp.SalesDocItem";
    private static final String DETAIL_ENTITY = "com.example.bp.SalesDocDetail";

    // ---------------------------------------------------------------
    // テスト用伝票番号
    // ---------------------------------------------------------------
    private static final String DOC_1 = "9900000001";
    private static final String DOC_2 = "9900000002";

    @Autowired
    private SalesDocumentHandler handler;

    @Autowired
    private PersistenceService db;

    @Autowired
    private PlatformTransactionManager txManager;

    /**
     * 外部S4サービスへの接続を防ぐためモックに差し替える。
     * saveDocuments() は runtime を使用しないため、振る舞いの設定は不要。
     */
    @MockitoBean
    private CdsRuntime runtime;

    /**
     * REQUIRES_NEW でコミットされたデータはテスト終了後も残るため、明示的に削除する。
     * 外部キー制約を考慮し、詳細 → 明細 → ヘッダ の順で削除する。
     */
    @AfterEach
    void cleanup() {
        new TransactionTemplate(txManager).execute(status -> {
            db.run(Delete.from(DETAIL_ENTITY));
            db.run(Delete.from(ITEM_ENTITY));
            db.run(Delete.from(HEADER_ENTITY));
            return null;
        });
    }

    // ====================================================================
    // 正常系
    // ====================================================================

    @Test
    @DisplayName("正常系: 2件の伝票が全て成功した場合、両方がコミットされる")
    void saveDocuments_allSuccess_allCommitted() {

        // given: 伝票1（明細2件・詳細3件）、伝票2（明細1件・詳細2件）
        Map<String, SalesDocumentHandler.SalesDocBuildResult> input = new LinkedHashMap<>();
        input.put(DOC_1, buildTestResult(DOC_1, 2, 3));
        input.put(DOC_2, buildTestResult(DOC_2, 1, 2));

        // when
        int[] counts = handler.saveDocuments(input);

        // then: 返却値の確認
        assertThat(counts[0]).as("headersCreated").isEqualTo(2);
        assertThat(counts[1]).as("itemsCreated").isEqualTo(3);    // 2 + 1
        assertThat(counts[2]).as("detailsCreated").isEqualTo(5);  // 3 + 2
        assertThat(counts[3]).as("errorCount").isEqualTo(0);

        // then: DB に両方の伝票が存在する
        assertThat(headerExists(DOC_1)).isTrue();
        assertThat(headerExists(DOC_2)).isTrue();
        assertThat(itemCount(DOC_1)).isEqualTo(2);
        assertThat(itemCount(DOC_2)).isEqualTo(1);
        assertThat(detailCount(DOC_1)).isEqualTo(3);
        assertThat(detailCount(DOC_2)).isEqualTo(2);
    }

    // ====================================================================
    // 異常系
    // ====================================================================

    @Test
    @DisplayName("異常系1: 2件目のヘッダINSERTが失敗した場合、1件目はコミット済みのまま残る")
    void saveDocuments_secondHeaderInsertFails_firstDocumentCommitted() {

        // given: DOC_2 のヘッダを事前登録 → saveDocuments で INSERT すると重複キーエラー
        preInsertHeader(DOC_2);

        Map<String, SalesDocumentHandler.SalesDocBuildResult> input = new LinkedHashMap<>();
        input.put(DOC_1, buildTestResult(DOC_1, 1, 2));  // 成功する
        input.put(DOC_2, buildTestResult(DOC_2, 1, 1));  // ヘッダINSERTで失敗

        // when
        int[] counts = handler.saveDocuments(input);

        // then: 返却値の確認
        assertThat(counts[0]).as("headersCreated: DOC_1 のみ").isEqualTo(1);
        assertThat(counts[3]).as("errorCount: DOC_2 が失敗").isEqualTo(1);

        // then: DOC_1 は COMMIT 済み → 明細・詳細が存在する
        assertThat(headerExists(DOC_1)).isTrue();
        assertThat(itemCount(DOC_1)).isEqualTo(1);
        assertThat(detailCount(DOC_1)).isEqualTo(2);

        // then: DOC_2 は ROLLBACK → 事前登録したヘッダのみ、明細・詳細は存在しない
        assertThat(itemCount(DOC_2)).isEqualTo(0);
        assertThat(detailCount(DOC_2)).isEqualTo(0);
    }

    @Test
    @DisplayName("異常系2: 詳細INSERTが失敗した場合、同一伝票のヘッダ・明細もロールバックされる")
    void saveDocuments_detailInsertFails_entireDocumentRolledBack() {

        // given: DOC_1 の詳細001を事前登録 → INSERT 時に重複キーエラー（ヘッダ・明細は成功後）
        preInsertDetail(DOC_1, "000010", "001");

        Map<String, SalesDocumentHandler.SalesDocBuildResult> input = new LinkedHashMap<>();
        input.put(DOC_1, buildTestResult(DOC_1, 1, 1));  // 詳細001を含む → 詳細INSERTで失敗
        input.put(DOC_2, buildTestResult(DOC_2, 1, 1));  // 成功する

        // when
        int[] counts = handler.saveDocuments(input);

        // then: 返却値の確認
        assertThat(counts[0]).as("headersCreated: DOC_2 のみ").isEqualTo(1);
        assertThat(counts[3]).as("errorCount: DOC_1 が失敗").isEqualTo(1);

        // then: DOC_1 は詳細INSERTでエラー
        //       → ヘッダ・明細も含めてトランザクション全体がROLLBACK
        //       （ヘッダINSERT → 明細INSERT が先に成功していても全て取り消される）
        assertThat(headerExists(DOC_1)).isFalse();
        assertThat(itemCount(DOC_1)).isEqualTo(0);

        // then: DOC_2 は COMMIT 済み → 存在する
        assertThat(headerExists(DOC_2)).isTrue();
        assertThat(itemCount(DOC_2)).isEqualTo(1);
        assertThat(detailCount(DOC_2)).isEqualTo(1);
    }

    @Test
    @DisplayName("異常系3: 全件失敗した場合、DBに何も登録されない")
    void saveDocuments_allFail_nothingCommitted() {

        // given: 両方を事前登録して全件失敗させる
        preInsertHeader(DOC_1);
        preInsertHeader(DOC_2);

        Map<String, SalesDocumentHandler.SalesDocBuildResult> input = new LinkedHashMap<>();
        input.put(DOC_1, buildTestResult(DOC_1, 1, 1));
        input.put(DOC_2, buildTestResult(DOC_2, 1, 1));

        // when
        int[] counts = handler.saveDocuments(input);

        // then: 返却値の確認
        assertThat(counts[0]).as("headersCreated").isEqualTo(0);
        assertThat(counts[3]).as("errorCount").isEqualTo(2);

        // then: 明細・詳細は存在しない（事前登録したヘッダのみ @AfterEach で削除される）
        assertThat(itemCount(DOC_1)).isEqualTo(0);
        assertThat(itemCount(DOC_2)).isEqualTo(0);
        assertThat(detailCount(DOC_1)).isEqualTo(0);
        assertThat(detailCount(DOC_2)).isEqualTo(0);
    }

    // ====================================================================
    // テストデータ構築ヘルパー
    // ====================================================================

    /**
     * テスト用 SalesDocBuildResult を組み立てる。
     * キーフィールドのみ設定し、他はデフォルト値を使用する。
     *
     * @param salesDocument 伝票番号
     * @param numItems      明細件数（明細番号: 000010, 000020, ...）
     * @param numDetails    詳細件数（連番: 001, 002, ...）
     */
    private SalesDocumentHandler.SalesDocBuildResult buildTestResult(
            String salesDocument, int numItems, int numDetails) {

        SalesDocHeader header = SalesDocHeader.create();
        header.setSalesDocument(salesDocument);
        header.setSalesOrganization("1000");
        header.setDistributionChannel("10");
        header.setDivision("00");
        header.setCustomerID("C0000001");
        header.setCurrency("JPY");
        header.setTotalNetAmount(BigDecimal.valueOf(10000 * numItems));
        header.setStatus("01");

        SalesDocumentHandler.SalesDocBuildResult result =
                new SalesDocumentHandler.SalesDocBuildResult(header, true);

        for (int i = 1; i <= numItems; i++) {
            String itemNum = String.format("%06d", i * 10);
            SalesDocItem item = SalesDocItem.create();
            item.setSalesDocument(salesDocument);
            item.setSalesDocumentItem(itemNum);
            item.setMaterialCode("MATNR00" + i);
            item.setOrderQuantity(BigDecimal.ONE);
            item.setOrderQuantityUnit("EA");
            item.setNetAmount(BigDecimal.valueOf(10000));
            item.setCurrency("JPY");
            result.itemEntries.add(new SalesDocumentHandler.SalesDocItemEntry(item, true));
        }

        for (int i = 1; i <= numDetails; i++) {
            SalesDocDetail detail = SalesDocDetail.create();
            detail.setSalesDocument(salesDocument);
            detail.setSalesDocumentItem("000010");
            detail.setSequentialNumber(String.format("%03d", i));
            detail.setDetailCategory("PR");
            detail.setCurrency("JPY");
            result.details.add(detail);
        }

        return result;
    }

    // ====================================================================
    // 事前登録ヘルパー（重複キーエラーを発生させるため）
    // ====================================================================

    /**
     * ヘッダを独立したトランザクションで事前登録する。
     * saveDocuments() が同じキーで INSERT した時に重複キーエラーが発生する。
     */
    private void preInsertHeader(String salesDocument) {
        SalesDocHeader header = SalesDocHeader.create();
        header.setSalesDocument(salesDocument);
        header.setSalesOrganization("1000");
        header.setDistributionChannel("10");
        header.setDivision("00");
        header.setCustomerID("C0000099"); // テスト用の別顧客
        header.setCurrency("JPY");
        header.setTotalNetAmount(BigDecimal.ZERO);
        header.setStatus("01");
        new TransactionTemplate(txManager).execute(status -> {
            db.run(Insert.into(HEADER_ENTITY).entry(header));
            return null;
        });
    }

    /**
     * 詳細を独立したトランザクションで事前登録する。
     * saveDocuments() が同じキーで INSERT した時に重複キーエラーが発生する。
     * これにより「ヘッダ・明細INSERT成功 → 詳細INSERTで失敗」シナリオを再現できる。
     */
    private void preInsertDetail(String salesDocument, String salesDocumentItem, String sequentialNumber) {
        SalesDocDetail detail = SalesDocDetail.create();
        detail.setSalesDocument(salesDocument);
        detail.setSalesDocumentItem(salesDocumentItem);
        detail.setSequentialNumber(sequentialNumber);
        detail.setDetailCategory("PR");
        detail.setCurrency("JPY");
        new TransactionTemplate(txManager).execute(status -> {
            db.run(Insert.into(DETAIL_ENTITY).entry(detail));
            return null;
        });
    }

    // ====================================================================
    // DB状態確認ヘルパー
    // ====================================================================

    private boolean headerExists(String salesDocument) {
        return db.run(
            Select.from(HEADER_ENTITY)
                  .where(h -> h.get("SalesDocument").eq(salesDocument))
        ).first().isPresent();
    }

    private int itemCount(String salesDocument) {
        return (int) db.run(
            Select.from(ITEM_ENTITY)
                  .where(i -> i.get("SalesDocument").eq(salesDocument))
        ).stream().count();
    }

    private int detailCount(String salesDocument) {
        return (int) db.run(
            Select.from(DETAIL_ENTITY)
                  .where(d -> d.get("SalesDocument").eq(salesDocument))
        ).stream().count();
    }
}
