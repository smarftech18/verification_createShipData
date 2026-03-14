// package customer.verification;

// import com.sap.cds.Result;
// import com.sap.cds.Row;
// import com.sap.cds.ql.Select;
// import com.sap.cds.ql.Update;
// import com.sap.cds.ql.Upsert;
// import com.sap.cds.services.EventContext;
// import com.sap.cds.services.cds.CqnService;
// import com.sap.cds.services.handler.EventHandler;
// import com.sap.cds.services.handler.annotations.On;
// import com.sap.cds.services.handler.annotations.ServiceName;
// import com.sap.cds.services.persistence.PersistenceService;
// import com.sap.cds.services.runtime.CdsRuntime;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import java.math.BigDecimal;
// import java.util.*;
// import java.util.stream.Collectors;

// /**
//  * SalesDocumentService のイベントハンドラ。
//  *
//  * <p>処理フロー:
//  * <ol>
//  *   <li>S4 CDS View (ZC_SALESDOCUMENT) を OData 経由で取得する</li>
//  *   <li>CDS View レコードをヘッダ → 明細 → 詳細の順にグループ化する</li>
//  *   <li>レコード単位で変動する検索条件でマスタ（シノニム経由）を取得する
//  *       <ul>
//  *         <li>CustomerMaster  : CustomerID + 販売エリア (ヘッダ単位)</li>
//  *         <li>MaterialMaster  : MaterialCode (明細単位)</li>
//  *         <li>PlantMaster     : Plant (明細単位)</li>
//  *       </ul>
//  *   </li>
//  *   <li>マスタ補完済みデータで SalesDocHeader / SalesDocItem / SalesDocDetail を UPSERT する</li>
//  * </ol>
//  */
// @Component
// @ServiceName("SalesDocumentService")
// public class SalesDocumentHandler implements EventHandler {

//     private static final Logger log = LoggerFactory.getLogger(SalesDocumentHandler.class);

//     /** 処理ステータス定数 */
//     private static final String STATUS_PROCESSING = "01";
//     private static final String STATUS_COMPLETED  = "02";
//     private static final String STATUS_ERROR      = "09";

//     /** CDS エンティティ FQN */
//     private static final String ENTITY_HEADER   = "com.example.bp.SalesDocHeader";
//     private static final String ENTITY_ITEM     = "com.example.bp.SalesDocItem";
//     private static final String ENTITY_DETAIL   = "com.example.bp.SalesDocDetail";
//     private static final String MASTER_CUSTOMER = "com.example.bp.master.CustomerMaster";
//     private static final String MASTER_MATERIAL = "com.example.bp.master.MaterialMaster";
//     private static final String MASTER_PLANT    = "com.example.bp.master.PlantMaster";
//     private static final String S4_VIEW         = "ZC_SALESDOCUMENT.ZC_SalesDocument";

//     @Autowired
//     private PersistenceService db;

//     @Autowired
//     private CdsRuntime runtime;

//     // ====================================================================
//     // createSalesDocuments アクション
//     // ====================================================================

//     /**
//      * S4 CDS View から売上伝票を生成する。
//      *
//      * @param ctx salesDocument (任意), forceUpdate (省略可, default false)
//      */
//     @On(event = "createSalesDocuments")
//     public void onCreateSalesDocuments(EventContext ctx) {

//         String  salesDocument = (String)  ctx.get("salesDocument");
//         Boolean forceUpdate   = (Boolean) ctx.getOrDefault("forceUpdate", false);

//         log.info("createSalesDocuments start: salesDocument={}, forceUpdate={}", salesDocument, forceUpdate);

//         int headersCreated = 0;
//         int itemsCreated   = 0;
//         int detailsCreated = 0;
//         int errorCount     = 0;

//         // ① S4 CDS View からソースデータを取得
//         List<Row> sourceRows = fetchFromS4CdsView(salesDocument);
//         if (sourceRows.isEmpty()) {
//             setResult(ctx, false, "No data found in S4 CDS View", 0, 0, 0, 0);
//             return;
//         }

//         // レコードをヘッダ(伝票番号)単位でグループ化
//         Map<String, List<Row>> byHeader = sourceRows.stream()
//             .collect(Collectors.groupingBy(r -> (String) r.get("SalesDocument"),
//                      LinkedHashMap::new, Collectors.toList()));

//         for (Map.Entry<String, List<Row>> headerEntry : byHeader.entrySet()) {
//             String    docNum   = headerEntry.getKey();
//             List<Row> docRows  = headerEntry.getValue();
//             Row       firstRow = docRows.get(0);

//             try {
//                 // 既存チェック（forceUpdate=false の場合はスキップ）
//                 if (!forceUpdate && headerExists(docNum)) {
//                     log.info("Skipping existing SalesDocument: {}", docNum);
//                     continue;
//                 }

//                 // ヘッダを処理中ステータスで先行作成
//                 Map<String, Object> header = buildHeader(firstRow);
//                 db.run(Upsert.into(ENTITY_HEADER).entry(header));

//                 // ② 得意先マスタ取得（CustomerID + 販売エリアで検索 → ヘッダ単位で変動）
//                 Row customerMaster = fetchCustomerMaster(
//                     (String) firstRow.get("CustomerID"),
//                     (String) firstRow.get("SalesOrganization"),
//                     (String) firstRow.get("DistributionChannel"),
//                     (String) firstRow.get("Division")
//                 );
//                 // マスタ補完値をヘッダに反映して更新
//                 applyCustomerMasterToHeader(docNum, customerMaster);
//                 headersCreated++;

//                 BigDecimal totalNetAmount = BigDecimal.ZERO;

//                 // 明細(SalesDocumentItem)単位でグループ化
//                 Map<String, List<Row>> byItem = docRows.stream()
//                     .collect(Collectors.groupingBy(r -> (String) r.get("SalesDocumentItem"),
//                              LinkedHashMap::new, Collectors.toList()));

//                 for (Map.Entry<String, List<Row>> itemEntry : byItem.entrySet()) {
//                     String    itemNum      = itemEntry.getKey();
//                     List<Row> itemRows     = itemEntry.getValue();
//                     Row       firstItemRow = itemRows.get(0);

//                     // ② 品目マスタ取得（MaterialCode で検索 → 明細単位で変動）
//                     Row materialMaster = fetchMaterialMaster(
//                         (String) firstItemRow.get("MaterialCode")
//                     );

//                     // ② プラントマスタ取得（Plant で検索 → 明細単位で変動）
//                     Row plantMaster = fetchPlantMaster(
//                         (String) firstItemRow.get("Plant")
//                     );

//                     // 明細作成
//                     Map<String, Object> item = buildItem(docNum, firstItemRow, materialMaster, plantMaster);
//                     db.run(Upsert.into(ENTITY_ITEM).entry(item));
//                     itemsCreated++;

//                     // 合計金額集計
//                     Object netAmt = firstItemRow.get("NetAmount");
//                     if (netAmt instanceof BigDecimal) {
//                         totalNetAmount = totalNetAmount.add((BigDecimal) netAmt);
//                     }

//                     // ③ 詳細（SequentialNumber）ごとに作成 — レコード単位で詳細区分/条件が変動
//                     for (Row detailRow : itemRows) {
//                         Map<String, Object> detail = buildDetail(docNum, itemNum, detailRow);
//                         db.run(Upsert.into(ENTITY_DETAIL).entry(detail));
//                         detailsCreated++;
//                     }
//                 }

//                 // ヘッダ合計金額更新・完了ステータス
//                 updateHeaderCompletion(docNum, totalNetAmount);

//             } catch (Exception e) {
//                 log.error("Error processing SalesDocument {}: {}", docNum, e.getMessage(), e);
//                 updateHeaderStatus(docNum, STATUS_ERROR, e.getMessage());
//                 errorCount++;
//             }
//         }

//         String msg = String.format(
//             "Completed. Headers=%d, Items=%d, Details=%d, Errors=%d",
//             headersCreated, itemsCreated, detailsCreated, errorCount);
//         log.info(msg);
//         setResult(ctx, errorCount == 0, msg, headersCreated, itemsCreated, detailsCreated, errorCount);
//     }

//     // ====================================================================
//     // getSalesDocStatus ファンクション
//     // ====================================================================

//     @On(event = "getSalesDocStatus")
//     public void onGetSalesDocStatus(EventContext ctx) {

//         String salesDocument = (String) ctx.get("salesDocument");

//         Result result = db.run(
//             Select.from(ENTITY_HEADER)
//                   .columns("SalesDocument", "Status", "ErrorMessage", "modifiedAt")
//                   .where(h -> h.get("SalesDocument").eq(salesDocument))
//         );

//         if (result.rowCount() == 0) {
//             ctx.setException(404, "SalesDocument not found: " + salesDocument);
//             return;
//         }

//         Row    row    = result.single();
//         String status = (String) row.get("Status");

//         ctx.put("salesDocument", salesDocument);
//         ctx.put("status",        status);
//         ctx.put("statusText",    resolveStatusText(status));
//         ctx.put("lastUpdated",   row.get("modifiedAt"));
//         ctx.put("errorMessage",  row.get("ErrorMessage"));
//     }

//     // ====================================================================
//     // Private: S4 CDS View 取得
//     // ====================================================================

//     /**
//      * 外部サービス (ZC_SALESDOCUMENT) を OData 経由で取得する。
//      * salesDocument が指定された場合は当該伝票のみ、未指定の場合は全件。
//      */
//     private List<Row> fetchFromS4CdsView(String salesDocument) {

//         CqnService s4Service = (CqnService) runtime.getServiceCatalog()
//             .getService(CqnService.class, "ZC_SALESDOCUMENT");

//         var query = Select.from(S4_VIEW)
//             .orderBy(q -> q.get("SalesDocument").asc(),
//                      q -> q.get("SalesDocumentItem").asc(),
//                      q -> q.get("SequentialNumber").asc());

//         if (salesDocument != null && !salesDocument.isBlank()) {
//             query = query.where(q -> q.get("SalesDocument").eq(salesDocument));
//         }

//         Result result = s4Service.run(query);
//         List<Row> rows = new ArrayList<>();
//         result.forEach(rows::add);

//         log.info("Fetched {} rows from {} (filter: SalesDocument={})",
//                  rows.size(), S4_VIEW, salesDocument);
//         return rows;
//     }

//     // ====================================================================
//     // Private: マスタ取得（検索条件は CDS View レコード単位で変動）
//     // ====================================================================

//     /**
//      * 得意先マスタを取得する。
//      * 検索条件の CustomerID + 販売エリアはヘッダレコードごとに変動する。
//      */
//     private Row fetchCustomerMaster(String customerId, String salesOrg,
//                                     String distChannel, String division) {
//         if (customerId == null || customerId.isBlank()) return null;

//         Result result = db.run(
//             Select.from(MASTER_CUSTOMER)
//                   .where(m -> m.get("CustomerID").eq(customerId)
//                           .and(m.get("SalesOrganization").eq(salesOrg))
//                           .and(m.get("DistributionChannel").eq(distChannel))
//                           .and(m.get("Division").eq(division)))
//         );

//         Row row = result.first().orElse(null);
//         if (row == null) {
//             log.warn("CustomerMaster not found: CustomerID={}, SalesOrg={}, DistCh={}, Div={}",
//                      customerId, salesOrg, distChannel, division);
//         }
//         return row;
//     }

//     /**
//      * 品目マスタを取得する。
//      * 検索条件の MaterialCode は明細レコードごとに変動する。
//      */
//     private Row fetchMaterialMaster(String materialCode) {
//         if (materialCode == null || materialCode.isBlank()) return null;

//         Result result = db.run(
//             Select.from(MASTER_MATERIAL)
//                   .where(m -> m.get("MaterialCode").eq(materialCode))
//         );

//         Row row = result.first().orElse(null);
//         if (row == null) {
//             log.warn("MaterialMaster not found: MaterialCode={}", materialCode);
//         }
//         return row;
//     }

//     /**
//      * プラントマスタを取得する。
//      * 検索条件の Plant は明細レコードごとに変動する。
//      */
//     private Row fetchPlantMaster(String plant) {
//         if (plant == null || plant.isBlank()) return null;

//         Result result = db.run(
//             Select.from(MASTER_PLANT)
//                   .where(m -> m.get("Plant").eq(plant))
//         );

//         Row row = result.first().orElse(null);
//         if (row == null) {
//             log.warn("PlantMaster not found: Plant={}", plant);
//         }
//         return row;
//     }

//     // ====================================================================
//     // Private: エンティティ構築
//     // ====================================================================

//     private boolean headerExists(String salesDocument) {
//         return db.run(
//             Select.from(ENTITY_HEADER)
//                   .columns("SalesDocument")
//                   .where(h -> h.get("SalesDocument").eq(salesDocument))
//         ).rowCount() > 0;
//     }

//     /** SalesDocHeader のマップを生成する（初期: 処理中ステータス）。 */
//     private Map<String, Object> buildHeader(Row src) {
//         Map<String, Object> h = new LinkedHashMap<>();
//         h.put("SalesDocument",       src.get("SalesDocument"));
//         h.put("SalesOrganization",   src.get("SalesOrganization"));
//         h.put("DistributionChannel", src.get("DistributionChannel"));
//         h.put("Division",            src.get("Division"));
//         h.put("SalesDocumentDate",   src.get("SalesDocumentDate"));
//         h.put("SalesDocumentType",   src.get("SalesDocumentType"));
//         h.put("CustomerID",          src.get("CustomerID"));
//         h.put("Currency",            src.get("Currency"));
//         h.put("TotalNetAmount",      BigDecimal.ZERO);
//         h.put("Status",              STATUS_PROCESSING);
//         return h;
//     }

//     /** CustomerMaster の補完値をヘッダへ反映する。 */
//     private void applyCustomerMasterToHeader(String salesDocument, Row customer) {
//         if (customer == null) return;

//         Map<String, Object> patch = new LinkedHashMap<>();
//         patch.put("CustomerName",  customer.get("CustomerName"));
//         patch.put("CustomerGroup", customer.get("CustomerGroup"));

//         db.run(Update.entity(ENTITY_HEADER)
//                      .data(patch)
//                      .where(h -> h.get("SalesDocument").eq(salesDocument)));
//     }

//     /** SalesDocItem のマップを生成する。 */
//     private Map<String, Object> buildItem(String salesDocument, Row src,
//                                           Row material, Row plant) {
//         Map<String, Object> item = new LinkedHashMap<>();
//         item.put("SalesDocument",     salesDocument);
//         item.put("SalesDocumentItem", src.get("SalesDocumentItem"));
//         item.put("MaterialCode",      src.get("MaterialCode"));
//         item.put("OrderQuantity",     src.get("OrderQuantity"));
//         item.put("OrderQuantityUnit", src.get("OrderQuantityUnit"));
//         item.put("NetAmount",         src.get("NetAmount"));
//         item.put("Currency",          src.get("Currency"));
//         item.put("Plant",             src.get("Plant"));
//         item.put("StorageLocation",   src.get("StorageLocation"));
//         item.put("PricingDate",       src.get("PricingDate"));

//         // マスタ補完
//         if (material != null) {
//             item.put("MaterialName",  material.get("MaterialName"));
//             item.put("MaterialGroup", material.get("MaterialGroup"));
//         }
//         if (plant != null) {
//             item.put("PlantName",   plant.get("PlantName"));
//             item.put("CompanyCode", plant.get("CompanyCode"));
//         }
//         return item;
//     }

//     /** SalesDocDetail のマップを生成する。 */
//     private Map<String, Object> buildDetail(String salesDocument,
//                                              String salesDocumentItem, Row src) {
//         Map<String, Object> d = new LinkedHashMap<>();
//         d.put("SalesDocument",       salesDocument);
//         d.put("SalesDocumentItem",   salesDocumentItem);
//         d.put("SequentialNumber",    src.get("SequentialNumber"));
//         d.put("DetailCategory",      src.get("DetailCategory"));
//         d.put("DetailText",          src.get("DetailText"));
//         d.put("DetailAmount",        src.get("DetailAmount"));
//         d.put("ConditionType",       src.get("ConditionType"));
//         d.put("ScheduleLineDate",    src.get("ScheduleLineDate"));
//         d.put("DeliveryScheduleQty", src.get("DeliveryScheduleQty"));
//         d.put("Currency",            src.get("Currency"));
//         return d;
//     }

//     // ====================================================================
//     // Private: ステータス更新
//     // ====================================================================

//     /** ヘッダを完了ステータスに更新し、合計金額を設定する。 */
//     private void updateHeaderCompletion(String salesDocument, BigDecimal totalNetAmount) {
//         Map<String, Object> patch = new LinkedHashMap<>();
//         patch.put("Status",         STATUS_COMPLETED);
//         patch.put("TotalNetAmount", totalNetAmount);
//         patch.put("ErrorMessage",   null);

//         db.run(Update.entity(ENTITY_HEADER)
//                      .data(patch)
//                      .where(h -> h.get("SalesDocument").eq(salesDocument)));
//     }

//     /** ヘッダのステータスのみを更新する（エラー時に使用）。 */
//     private void updateHeaderStatus(String salesDocument, String status, String errorMsg) {
//         try {
//             Map<String, Object> patch = new LinkedHashMap<>();
//             patch.put("Status",       status);
//             patch.put("ErrorMessage", errorMsg != null && errorMsg.length() > 255
//                                       ? errorMsg.substring(0, 255) : errorMsg);
//             db.run(Update.entity(ENTITY_HEADER)
//                          .data(patch)
//                          .where(h -> h.get("SalesDocument").eq(salesDocument)));
//         } catch (Exception e) {
//             log.error("Failed to update header status for {}: {}", salesDocument, e.getMessage());
//         }
//     }

//     // ====================================================================
//     // Private: ユーティリティ
//     // ====================================================================

//     private void setResult(EventContext ctx, boolean success, String message,
//                            int headers, int items, int details, int errors) {
//         ctx.put("success",        success);
//         ctx.put("message",        message);
//         ctx.put("headersCreated", headers);
//         ctx.put("itemsCreated",   items);
//         ctx.put("detailsCreated", details);
//         ctx.put("errorCount",     errors);
//     }

//     private String resolveStatusText(String status) {
//         if (status == null) return "不明";
//         return switch (status) {
//             case STATUS_PROCESSING -> "処理中";
//             case STATUS_COMPLETED  -> "完了";
//             case STATUS_ERROR      -> "エラー";
//             default                -> "不明";
//         };
//     }
// }
