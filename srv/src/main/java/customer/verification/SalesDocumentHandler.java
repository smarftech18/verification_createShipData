package customer.verification;

import cds.gen.com.example.bp.SalesDocDetail;
import cds.gen.com.example.bp.SalesDocHeader;
import cds.gen.com.example.bp.SalesDocItem;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SalesDocumentService のイベントハンドラ。
 *
 * <p>処理フロー（createSalesDocuments アクション）:
 * <ol>
 *   <li>インプットパラメータ（伝票番号）をイベントコンテキストから取得する</li>
 *   <li>ZC_SALESDOCUMENT_SERVICE から対象伝票データを取得する</li>
 *   <li>S4レコード単位でマスタデータを取得しエンティティを作成する（Map で新規/既存判定）</li>
 *   <li>伝票番号単位で独立したトランザクションでDBに登録する</li>
 * </ol>
 *
 * <p>【トランザクション設計】
 * CAP @On ハンドラは1リクエスト = 1 CAP ChangeSet（Springトランザクション）で実行される。
 * 伝票単位の独立したトランザクションは {@link TransactionTemplate}（REQUIRES_NEW）で実現する。
 * 詳細は {@link #init()} および {@link #saveDocuments} を参照。
 *
 * <p>NOTE: マスタデータ取得方法は SalesDocItemView を介する方式を採用しているが、
 * 将来的に取得方法が変わる可能性があるため、{@link #fetchMasterData} に集約している。
 */
@Component
@ServiceName("SalesDocumentService")
public class SalesDocumentHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(SalesDocumentHandler.class);

    // ---------------------------------------------------------------
    // 処理ステータス定数
    // ---------------------------------------------------------------
    private static final String STATUS_PROCESSING = "01";

    // ---------------------------------------------------------------
    // CDS エンティティ / サービス名定数
    // ---------------------------------------------------------------
    /** 外部サービス名（ZC_SALESDOCUMENT_SERVICE.cds のサービス名と一致させる） */
    private static final String S4_SERVICE_NAME = "ZC_SALESDOCUMENT_SERVICE";

    /** ZcSalesDocument のフルパス（外部サービス名.エンティティ名） */
    private static final String S4_ENTITY       = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument";

    /** SalesDocItemView のフルパス（サービス名.ビュー名） */
    private static final String MASTER_VIEW      = "SalesDocumentService.SalesDocItemView";

    /** 登録先エンティティパス（スキーマの名前空間.エンティティ名） */
    private static final String HEADER_ENTITY = "com.example.bp.SalesDocHeader";
    private static final String ITEM_ENTITY   = "com.example.bp.SalesDocItem";
    private static final String DETAIL_ENTITY = "com.example.bp.SalesDocDetail";

    // ---------------------------------------------------------------
    // 依存サービス
    // ---------------------------------------------------------------
    @Autowired
    private PersistenceService db;

    @Autowired
    private CdsRuntime runtime;

    @Autowired
    private PlatformTransactionManager txManager;

    /** 伝票番号単位の独立したトランザクション制御に使用 */
    private TransactionTemplate transactionTemplate;

    /**
     * トランザクション設定の初期化。
     *
     * <p>【なぜ TransactionTemplate を使うか】
     * Spring の @Transactional アノテーションはプロキシ経由で動作する。
     * 同一クラス内の自己呼び出し（self-call）ではプロキシを経由しないため
     * @Transactional が機能しない。
     * TransactionTemplate はプロキシ不要でプログラム的にトランザクションを制御できるため、
     * 同一クラス内で伝票単位のトランザクション分割を実現できる。
     *
     * <p>【PROPAGATION_REQUIRES_NEW の動作】
     * <pre>
     * CAP @On ハンドラ（外側トランザクション = CAP ChangeSet）
     *   └─ transactionTemplate.execute() が呼ばれると:
     *        ① 外側トランザクションを一時停止（suspend）
     *        ② 新しい独立したトランザクションを開始
     *        ③ db.run() が新トランザクションに参加
     *        ④ execute() の終了時に commit または rollback
     *        ⑤ 外側トランザクションを再開（resume）
     * </pre>
     * → ある伝票がコミットされた後で別伝票がエラーになっても、
     *   外側の CAP ChangeSet がロールバックしても、
     *   既にコミットされた伝票のデータは DB に残る。
     */
    @PostConstruct
    private void init() {
        transactionTemplate = new TransactionTemplate(txManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ====================================================================
    // 内部データクラス
    // ====================================================================

    /**
     * 伝票単位の作成データを保持する。
     * DB登録は行わず、データ構造のみを保持する（登録は別機能が担う）。
     */
    /**
     * 明細と新規フラグをペアで保持する。
     */
    public static class SalesDocItemEntry {
        public final SalesDocItem item;
        /** 新規作成された明細か（同一キーがMapに存在しなかった場合に true） */
        public final boolean      isNew;

        public SalesDocItemEntry(SalesDocItem item, boolean isNew) {
            this.item  = item;
            this.isNew = isNew;
        }
    }

    /**
     * 伝票単位の作成データを保持する。
     * DB登録は行わず、データ構造のみを保持する（登録は別機能が担う）。
     */
    public static class SalesDocBuildResult {
        public final SalesDocHeader           header;
        public final List<SalesDocItemEntry>  itemEntries = new ArrayList<>();
        public final List<SalesDocDetail>     details     = new ArrayList<>();
        /** 新規作成されたヘッダか（同一伝票番号キーがMapに存在しなかった場合に true） */
        public final boolean                  headerIsNew;

        public SalesDocBuildResult(SalesDocHeader header, boolean headerIsNew) {
            this.header      = header;
            this.headerIsNew = headerIsNew;
        }

        /** 後方互換用: 明細エンティティのみのリストを返す */
        public List<SalesDocItem> getItems() {
            List<SalesDocItem> list = new ArrayList<>();
            for (SalesDocItemEntry e : itemEntries) list.add(e.item);
            return list;
        }
    }

    // ====================================================================
    // createSalesDocuments アクション
    // ====================================================================

    /**
     * 売上伝票データを作成する。
     *
     * @param ctx salesDocument（必須）, forceUpdate（省略可, default false）
     */
    @On(event = "createSalesDocuments")
    public void onCreateSalesDocuments(EventContext ctx) {

        // STEP1: インプットパラメータ取得
        String salesDocument = (String) ctx.get("salesDocument");

        log.info("createSalesDocuments start: salesDocument={}", salesDocument);

        // STEP2: ZcSalesDocument から対象データ取得
        List<Row> s4Records = fetchZcSalesDocuments(salesDocument);
        if (s4Records.isEmpty()) {
            setResult(ctx, false, "No data found in ZcSalesDocument: " + salesDocument, 0, 0, 0, 0);
            return;
        }

        // STEP3/4/5: S4レコード単位でマスタ取得 → エンティティ作成 → 伝票番号単位で返却
        //   Map によりヘッダ・明細の新規/既存を判定しながら処理する
        //   NOTE: マスタ取得方法が変わる場合は fetchMasterData のみ修正する
        Map<String, SalesDocBuildResult> buildResults = buildSalesDocuments(s4Records);

        // STEP6: 伝票番号単位でDB登録
        //   1伝票の登録失敗が他伝票に影響しないよう、トランザクションは伝票番号単位で独立
        int[] counts = saveDocuments(buildResults);
        // counts[0]=headersCreated, [1]=itemsCreated, [2]=detailsCreated, [3]=errorCount

        String msg = String.format(
            "Completed. Headers=%d, Items=%d, Details=%d, Errors=%d",
            counts[0], counts[1], counts[2], counts[3]);
        log.info(msg);

        boolean success = counts[3] == 0;
        setResult(ctx, success, msg, counts[0], counts[1], counts[2], counts[3]);
    }

    // ====================================================================
    // STEP2: ZcSalesDocument 取得
    // ====================================================================

    /**
     * 外部サービス ZC_SALESDOCUMENT_SERVICE から伝票データを取得する。
     *
     * @param salesDocument 伝票番号（インプットパラメータ）
     * @return 対象伝票の全レコード（ヘッダ・明細・詳細の組み合わせ）
     */
    private List<Row> fetchZcSalesDocuments(String salesDocument) {

        CqnService s4Service = (CqnService) runtime.getServiceCatalog()
            .getService(CqnService.class, S4_SERVICE_NAME);

        // 伝票番号を検索条件として使用
        // 順序: 伝票番号 → 明細番号 → 連番 でソートし処理順序を安定させる
        var query = Select.from(S4_ENTITY)
            .where(q -> q.get("SalesDocument").eq(salesDocument))
            .orderBy(
                q -> q.get("SalesDocument").asc(),
                q -> q.get("SalesDocumentItem").asc(),
                q -> q.get("SequentialNumber").asc()
            );

        Result result = s4Service.run(query);
        List<Row> rows = new ArrayList<>();
        result.forEach(rows::add);

        log.info("Fetched {} records from ZcSalesDocument (SalesDocument={})",
                 rows.size(), salesDocument);
        return rows;
    }

    // ====================================================================
    // STEP4: マスタデータ取得（S4レコード単位）
    // ====================================================================

    /**
     * SalesDocItemView から1レコード分のマスタ補完データを取得する。
     *
     * <p>S4レコードの各項目値を検索条件として使用する。
     * レコードによって CustomerID・販売エリア・品目・プラントの組み合わせが異なるため、
     * レコード単位で都度取得する。
     *
     * <p>NOTE: SalesDocItemView の起点テーブルや結合方法が変わる場合でも、
     * このメソッドのインターフェース（引数/戻り値）は変わらないため、
     * 呼び出し元への影響を局所化できる。
     *
     * @param s4Record ZcSalesDocument の1レコード（検索条件の値を持つ）
     * @return マスタ補完Row。対象データが存在しない場合は null
     */
    private Row fetchMasterData(Row s4Record) {

        // S4レコードの項目値を検索条件として使用
        // レコードによってこれらの値が変動するため、レコード単位で取得する
        String salesDocument      = (String) s4Record.get("SalesDocument");
        String salesDocumentItem  = (String) s4Record.get("SalesDocumentItem");
        String salesOrganization  = (String) s4Record.get("SalesOrganization");
        String distributionChannel = (String) s4Record.get("DistributionChannel");
        String division           = (String) s4Record.get("Division");
        String customerId         = (String) s4Record.get("CustomerID");

        Result result = db.run(
            Select.from(MASTER_VIEW)
                  .where(v -> v.get("SalesDocument")      .eq(salesDocument)
                         .and(v.get("SalesDocumentItem")   .eq(salesDocumentItem))
                         .and(v.get("SalesOrganization")   .eq(salesOrganization))
                         .and(v.get("DistributionChannel") .eq(distributionChannel))
                         .and(v.get("Division")            .eq(division))
                         .and(v.get("CustomerID")          .eq(customerId)))
        );

        Row master = result.first().orElse(null);
        if (master == null) {
            log.warn("Master not found: SalesDocument={}, SalesDocumentItem={}, CustomerID={}",
                     salesDocument, salesDocumentItem, customerId);
        }
        return master;
    }

    // ====================================================================
    // STEP4/5: エンティティへのマッピング・伝票番号単位で返却
    // ====================================================================

    /**
     * ZcSalesDocument レコードを伝票番号単位で処理し、3種類のエンティティを組み立てる。
     *
     * <p>S4レコード単位でマスタデータを取得してからエンティティを作成する。
     * DB登録は行わない。呼び出し元がリストを受け取り、別機能で登録する。
     *
     * <p>ZcSalesDocument のキー構成: SalesDocument + SalesDocumentItem + SequentialNumber
     * <ul>
     *   <li>ヘッダキー（SalesDocument）が初出 → 新規ヘッダ（headerIsNew=true）</li>
     *   <li>明細キー（SalesDocument_SalesDocumentItem）が初出 → 新規明細（isNew=true）</li>
     *   <li>同一ヘッダ/明細キーの2件目以降 → 新規詳細のみ追加</li>
     * </ul>
     *
     * @param s4Records ZcSalesDocument の全レコード（SalesDocument→Item→SequentialNumber順）
     * @return 伝票番号 → SalesDocBuildResult のMap
     */
    private Map<String, SalesDocBuildResult> buildSalesDocuments(List<Row> s4Records) {

        // 結果Map（取得順序を保持）
        Map<String, SalesDocBuildResult> results = new LinkedHashMap<>();

        // ヘッダ新規/既存判定用: key = SalesDocument
        Map<String, SalesDocHeader> headerSeenMap = new LinkedHashMap<>();
        // 明細新規/既存判定用: key = SalesDocument + "_" + SalesDocumentItem
        Map<String, SalesDocItem>   itemSeenMap   = new LinkedHashMap<>();

        for (Row rec : s4Records) {
            String salesDocument     = (String) rec.get("SalesDocument");
            String salesDocumentItem = (String) rec.get("SalesDocumentItem");
            String headerKey = salesDocument;
            String itemKey   = salesDocument + "_" + salesDocumentItem;

            // ---- ヘッダ ----
            // headerSeenMap にキーが存在しない = このレコードで初めて登場した伝票番号 → 新規
            boolean headerIsNew = !headerSeenMap.containsKey(headerKey);
            if (headerIsNew) {
                // CustomerMaster の補完値はヘッダ単位で取得する（伝票の先頭レコードのみ）
                Row headerMaster = fetchMasterData(rec);
                SalesDocHeader header = buildHeader(rec, headerMaster);
                headerSeenMap.put(headerKey, header);
                results.put(salesDocument, new SalesDocBuildResult(header, true));
                log.debug("New header: SalesDocument={}", salesDocument);
            }

            SalesDocBuildResult buildResult = results.get(salesDocument);

            // ---- 明細 ----
            // itemSeenMap にキーが存在しない = この明細番号の初回登場 → 新規
            boolean itemIsNew = !itemSeenMap.containsKey(itemKey);
            if (itemIsNew) {
                // MaterialMaster / PlantMaster の補完値は明細単位で取得する（明細の先頭レコードのみ）
                Row itemMaster = fetchMasterData(rec);
                SalesDocItem item = buildItem(rec, itemMaster);
                itemSeenMap.put(itemKey, item);
                buildResult.itemEntries.add(new SalesDocItemEntry(item, true));

                // 合計金額集計（明細の初回登場時のみ加算）
                Object netAmt = rec.get("NetAmount");
                if (netAmt instanceof BigDecimal bd) {
                    BigDecimal current = buildResult.header.getTotalNetAmount();
                    buildResult.header.setTotalNetAmount(
                        (current != null ? current : BigDecimal.ZERO).add(bd));
                }
                log.debug("New item: SalesDocument={}, SalesDocumentItem={}", salesDocument, salesDocumentItem);
            }

            // ---- 詳細 ----
            // ZcSalesDocument の1レコード = 1詳細（SequentialNumber 単位で必ず新規）
            SalesDocDetail detail = buildDetail(rec);
            buildResult.details.add(detail);
        }

        return results;
    }

    // ====================================================================
    // STEP6: DB登録（伝票番号単位トランザクション）
    // ====================================================================

    /**
     * 伝票番号単位でヘッダ・明細・詳細をDBに登録する。
     *
     * <p>【トランザクション分離の仕組み】
     * <pre>
     * CAP @On ハンドラ（外側トランザクション）
     *   ├─ 伝票A: transactionTemplate.execute() → 独立トランザクション → COMMIT ✅
     *   ├─ 伝票B: transactionTemplate.execute() → 独立トランザクション → COMMIT ✅
     *   └─ 伝票C: transactionTemplate.execute() → 独立トランザクション → ROLLBACK ❌
     *                                                ↑ 例外が発生した場合
     * → 伝票A・Bのデータは残る。伝票Cのデータのみ取り消される。
     * </pre>
     *
     * <p>【ROLLBACK のトリガー】
     * transactionTemplate.execute() 内で RuntimeException が throw されると
     * Spring が自動で対象トランザクションをロールバックする。
     * その例外はここの catch で捕捉し、errorCount に記録して処理を継続する。
     *
     * @param buildResults 伝票番号 → SalesDocBuildResult のMap
     * @return int[4] { headersCreated, itemsCreated, detailsCreated, errorCount }
     */
    private int[] saveDocuments(Map<String, SalesDocBuildResult> buildResults) {

        int headersCreated = 0;
        int itemsCreated   = 0;
        int detailsCreated = 0;
        int errorCount     = 0;

        for (Map.Entry<String, SalesDocBuildResult> entry : buildResults.entrySet()) {
            String              docNum = entry.getKey();
            SalesDocBuildResult result = entry.getValue();

            try {
                // -------------------------------------------------------
                // 【トランザクション境界】
                // execute() の開始: 外側トランザクションを suspend し、
                //                   この伝票専用の新しいトランザクションを開始
                // execute() の終了: 例外がなければ COMMIT、あれば ROLLBACK
                // -------------------------------------------------------
                transactionTemplate.execute(status -> {

                    // ① ヘッダ登録（1件）
                    db.run(Insert.into(HEADER_ENTITY).entry(result.header));

                    // ② 明細登録（明細番号単位, 複数件）
                    //    entries() でバルクINSERT → 明細数が増えてもDB往復1回
                    List<SalesDocItem> items = result.getItems();
                    if (!items.isEmpty()) {
                        db.run(Insert.into(ITEM_ENTITY).entries(items));
                    }

                    // ③ 詳細登録（連番単位, 複数件）
                    //    entries() でバルクINSERT → 詳細数が増えてもDB往復1回
                    if (!result.details.isEmpty()) {
                        db.run(Insert.into(DETAIL_ENTITY).entries(result.details));
                    }

                    // ① ② ③ がすべて成功した場合にここに到達し COMMIT される
                    // ① の後に ② でエラーが起きた場合 → ① ② ③ すべてROLLBACK
                    return null;
                });
                // -------------------------------------------------------
                // ここに到達 = COMMIT 済み
                // -------------------------------------------------------

                headersCreated += 1;
                itemsCreated   += result.itemEntries.size();
                detailsCreated += result.details.size();
                log.info("Saved: SalesDocument={}, items={}, details={}",
                         docNum, result.itemEntries.size(), result.details.size());

            } catch (Exception e) {
                // transactionTemplate.execute() 内で例外が発生した場合にここに到達
                // 対象伝票のトランザクションはすでに ROLLBACK 済み
                // 他伝票の処理は継続する（ループを抜けない）
                errorCount++;
                log.error("Failed to save SalesDocument={}: {}", docNum, e.getMessage(), e);
            }
        }

        return new int[]{ headersCreated, itemsCreated, detailsCreated, errorCount };
    }

    // ====================================================================
    // エンティティマッピング（フィールドへの値設定）
    // ====================================================================

    /**
     * SalesDocHeader を組み立てる。
     *
     * <p>値の取得元:
     * <ul>
     *   <li>ZcSalesDocument: SalesDocument, SalesOrganization, DistributionChannel,
     *       Division, SalesDocumentDate, SalesDocumentType, CustomerID, Currency</li>
     *   <li>SalesDocItemView（CustomerMaster補完）: CustomerName, CustomerGroup</li>
     * </ul>
     *
     * @param src    ZcSalesDocument の先頭レコード（ヘッダレベル項目を持つ）
     * @param master SalesDocItemView のマスタ補完Row（null の場合はマスタ項目を空で設定）
     */
    private SalesDocHeader buildHeader(Row src, Row master) {
        SalesDocHeader h = SalesDocHeader.create();

        // ZcSalesDocument から
        h.setSalesDocument      ((String)     src.get("SalesDocument"));
        h.setSalesOrganization  ((String)     src.get("SalesOrganization"));
        h.setDistributionChannel((String)     src.get("DistributionChannel"));
        h.setDivision           ((String)     src.get("Division"));
        h.setSalesDocumentDate  (             src.get("SalesDocumentDate"));
        h.setSalesDocumentType  ((String)     src.get("SalesDocumentType"));
        h.setCustomerId         ((String)     src.get("CustomerID"));
        h.setCurrency           ((String)     src.get("Currency"));
        h.setTotalNetAmount     (BigDecimal.ZERO); // buildSalesDocuments で後から合計値を設定
        h.setStatus             (STATUS_PROCESSING);

        // SalesDocItemView（CustomerMaster補完）から
        if (master != null) {
            h.setCustomerName ((String) master.get("CustomerName"));
            h.setCustomerGroup((String) master.get("CustomerGroup"));
        } else {
            log.warn("CustomerMaster not found for SalesDocument={}", src.get("SalesDocument"));
        }

        return h;
    }

    /**
     * SalesDocItem を組み立てる。
     *
     * <p>値の取得元:
     * <ul>
     *   <li>ZcSalesDocument: SalesDocument, SalesDocumentItem, MaterialCode,
     *       OrderQuantity, OrderQuantityUnit, NetAmount, Currency,
     *       Plant, StorageLocation, PricingDate</li>
     *   <li>SalesDocItemView（MaterialMaster補完）: MaterialName, MaterialGroup</li>
     *   <li>SalesDocItemView（PlantMaster補完）: PlantName, CompanyCode</li>
     * </ul>
     *
     * @param src    ZcSalesDocument の明細先頭レコード（明細レベル項目を持つ）
     * @param master SalesDocItemView のマスタ補完Row（null の場合はマスタ項目を空で設定）
     */
    private SalesDocItem buildItem(Row src, Row master) {
        SalesDocItem item = SalesDocItem.create();

        // ZcSalesDocument から
        item.setSalesDocument    ((String)     src.get("SalesDocument"));
        item.setSalesDocumentItem((String)     src.get("SalesDocumentItem"));
        item.setMaterialCode     ((String)     src.get("MaterialCode"));
        item.setOrderQuantity    ((BigDecimal) src.get("OrderQuantity"));
        item.setOrderQuantityUnit((String)     src.get("OrderQuantityUnit"));
        item.setNetAmount        ((BigDecimal) src.get("NetAmount"));
        item.setCurrency         ((String)     src.get("Currency"));
        item.setPlant            ((String)     src.get("Plant"));
        item.setStorageLocation  ((String)     src.get("StorageLocation"));
        item.setPricingDate      (             src.get("PricingDate"));

        // SalesDocItemView（MaterialMaster / PlantMaster補完）から
        if (master != null) {
            item.setMaterialName ((String) master.get("MaterialName"));
            item.setMaterialGroup((String) master.get("MaterialGroup"));
            item.setPlantName    ((String) master.get("PlantName"));
            item.setCompanyCode  ((String) master.get("CompanyCode"));
        } else {
            log.warn("Master not found for SalesDocument={}, SalesDocumentItem={}",
                     src.get("SalesDocument"), src.get("SalesDocumentItem"));
        }

        return item;
    }

    /**
     * SalesDocDetail を組み立てる。
     *
     * <p>値の取得元: ZcSalesDocument のみ（詳細レベル項目はすべて S4 から取得）
     *
     * @param src ZcSalesDocument の詳細レコード（SequentialNumber 単位）
     */
    private SalesDocDetail buildDetail(Row src) {
        SalesDocDetail d = SalesDocDetail.create();

        // ZcSalesDocument から（全フィールド）
        d.setSalesDocument      ((String)     src.get("SalesDocument"));
        d.setSalesDocumentItem  ((String)     src.get("SalesDocumentItem"));
        d.setSequentialNumber   ((String)     src.get("SequentialNumber"));
        d.setDetailCategory     ((String)     src.get("DetailCategory"));
        d.setDetailText         ((String)     src.get("DetailText"));
        d.setDetailAmount       ((BigDecimal) src.get("DetailAmount"));
        d.setConditionType      ((String)     src.get("ConditionType"));
        d.setScheduleLineDate   (             src.get("ScheduleLineDate"));
        d.setDeliveryScheduleQty((BigDecimal) src.get("DeliveryScheduleQty"));
        d.setCurrency           ((String)     src.get("Currency"));

        return d;
    }

    // ====================================================================
    // ユーティリティ
    // ====================================================================

    private void setResult(EventContext ctx, boolean success, String message,
                           int headers, int items, int details, int errors) {
        ctx.put("success",        success);
        ctx.put("message",        message);
        ctx.put("headersCreated", headers);
        ctx.put("itemsCreated",   items);
        ctx.put("detailsCreated", details);
        ctx.put("errorCount",     errors);
    }
}
