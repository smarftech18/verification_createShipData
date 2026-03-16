package customer.verification;

import cds.gen.com.example.bp.SalesDocDetail;
import cds.gen.com.example.bp.SalesDocHeader;
import cds.gen.com.example.bp.SalesDocItem;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
 *   <li>取得データを伝票番号でグループ化する</li>
 *   <li>S4レコード単位で SalesDocItemView からマスタデータを取得しエンティティを作成する</li>
 *   <li>伝票番号単位のデータ作成結果を返却する（DB登録は別機能が担う）</li>
 * </ol>
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

    // ---------------------------------------------------------------
    // 依存サービス
    // ---------------------------------------------------------------
    @Autowired
    private PersistenceService db;

    @Autowired
    private CdsRuntime runtime;

    // ====================================================================
    // 内部データクラス
    // ====================================================================

    /**
     * 伝票単位の作成データを保持する。
     * DB登録は行わず、データ構造のみを保持する（登録は別機能が担う）。
     */
    public static class SalesDocBuildResult {
        public final SalesDocHeader       header;
        public final List<SalesDocItem>   items   = new ArrayList<>();
        public final List<SalesDocDetail> details = new ArrayList<>();

        public SalesDocBuildResult(SalesDocHeader header) {
            this.header = header;
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

        // STEP3: 伝票番号でグループ化
        //   LinkedHashMap で取得順序を保持する
        Map<String, List<Row>> byDocument = s4Records.stream()
            .collect(Collectors.groupingBy(
                r -> (String) r.get("SalesDocument"),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        // STEP4/5: S4レコード単位でマスタ取得 → エンティティ作成 → 伝票番号単位で返却
        //   NOTE: マスタ取得方法が変わる場合は fetchMasterData のみ修正する
        Map<String, SalesDocBuildResult> buildResults = buildSalesDocuments(byDocument);

        // 件数集計
        int headerCount = buildResults.size();
        int itemCount   = buildResults.values().stream()
                              .mapToInt(r -> r.items.size()).sum();
        int detailCount = buildResults.values().stream()
                              .mapToInt(r -> r.details.size()).sum();

        String msg = String.format(
            "Build completed. Headers=%d, Items=%d, Details=%d",
            headerCount, itemCount, detailCount);
        log.info(msg);

        setResult(ctx, true, msg, headerCount, itemCount, detailCount, 0);
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
     * @param byDocument 伝票番号でグループ化した ZcSalesDocument レコード
     * @return 伝票番号 → SalesDocBuildResult のMap
     */
    private Map<String, SalesDocBuildResult> buildSalesDocuments(
            Map<String, List<Row>> byDocument) {

        // 結果Map（取得順序を保持）
        Map<String, SalesDocBuildResult> results = new LinkedHashMap<>();

        for (Map.Entry<String, List<Row>> docEntry : byDocument.entrySet()) {
            String    docNum  = docEntry.getKey();
            List<Row> docRecs = docEntry.getValue();
            Row       first   = docRecs.get(0);

            // ---- ヘッダ作成 ----
            // CustomerMaster の補完値はヘッダ単位で取得する
            // 先頭明細レコードの条件でマスタを取得する（ヘッダ共通のマスタ値）
            Row headerMaster = fetchMasterData(first);

            SalesDocHeader      header      = buildHeader(first, headerMaster);
            SalesDocBuildResult buildResult = new SalesDocBuildResult(header);

            // 明細番号でグループ化
            Map<String, List<Row>> byItem = docRecs.stream()
                .collect(Collectors.groupingBy(
                    r -> (String) r.get("SalesDocumentItem"),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));

            BigDecimal totalNetAmount = BigDecimal.ZERO;

            for (Map.Entry<String, List<Row>> itemEntry : byItem.entrySet()) {
                List<Row> itemRecs  = itemEntry.getValue();
                Row       firstItem = itemRecs.get(0);

                // ---- 明細作成 ----
                // MaterialMaster / PlantMaster の補完値は明細単位で取得する
                // 明細レコードの条件でマスタを取得する（明細ごとに品目・プラントが変動）
                Row itemMaster = fetchMasterData(firstItem);

                SalesDocItem item = buildItem(firstItem, itemMaster);
                buildResult.items.add(item);

                // 合計金額集計（ヘッダ更新用）
                Object netAmt = firstItem.get("NetAmount");
                if (netAmt instanceof BigDecimal bd) {
                    totalNetAmount = totalNetAmount.add(bd);
                }

                // ---- 詳細作成（SequentialNumber 単位） ----
                // ZcSalesDocument の1レコード = 1詳細（連番単位で詳細区分・条件が変動）
                for (Row detailRec : itemRecs) {
                    SalesDocDetail detail = buildDetail(detailRec);
                    buildResult.details.add(detail);
                }
            }

            // 合計金額をヘッダに反映
            header.setTotalNetAmount(totalNetAmount);

            results.put(docNum, buildResult);
        }

        return results;
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
