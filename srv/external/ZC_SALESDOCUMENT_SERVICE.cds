// cds import ZC_SALESDOCUMENT.edmx によって生成される想定のファイル。
// Source: S/4HANA CDS View - 売上伝票統合ビュー (OData V4)
// Key: SalesDocument (伝票番号) / SalesDocumentItem (明細番号) / SequentialNumber (連番)

/* checksum : 9b2e4f1a7c0d3e8b */

@cds.external        : true
@cds.persistence.skip: true
service ZC_SALESDOCUMENT_SERVICE {

  @cds.external        : true
  @cds.persistence.skip: true
  @Common.Label        : 'Sales Document CDS View'
  entity ZcSalesDocument {
    // ----------------------------------------------------------------
    // キー項目
    // ----------------------------------------------------------------
    key SalesDocument        : String(10);   // 売上伝票番号
    key SalesDocumentItem    : String(6);    // 明細番号
    key SequentialNumber     : String(3);    // 連番（詳細番号）

    // ----------------------------------------------------------------
    // ヘッダレベル項目（SalesDocumentが同一のレコードで共通）
    // ----------------------------------------------------------------
        SalesOrganization    : String(4);    // 販売組織
        DistributionChannel  : String(2);    // 流通チャネル
        Division             : String(2);    // 製品部門
        SalesDocumentDate    : Date;         // 伝票日付
        SalesDocumentType    : String(4);    // 伝票種別 (例: OR, ZOR)
        CustomerID           : String(10);   // 得意先コード

    // ----------------------------------------------------------------
    // 明細レベル項目（SalesDocument + SalesDocumentItemが同一で共通）
    // ----------------------------------------------------------------
        MaterialCode         : String(18);   // 品目コード
        OrderQuantity        : Decimal(13, 3); // 注文数量
        OrderQuantityUnit    : String(3);    // 数量単位
        NetAmount            : Decimal(15, 2); // 正味金額
        Currency             : String(5);    // 通貨コード
        Plant                : String(4);    // プラント
        StorageLocation      : String(4);    // 保管場所
        PricingDate          : Date;         // 価格決定日

    // ----------------------------------------------------------------
    // 詳細レベル項目（SequentialNumber単位で変動）
    // ----------------------------------------------------------------
        DetailCategory       : String(2);    // 詳細区分 (PR=価格条件, SL=スケジュールライン等)
        DetailText           : String(255);  // 詳細テキスト
        DetailAmount         : Decimal(15, 2); // 詳細金額
        ConditionType        : String(4);    // 条件タイプ (例: PR00, MWST)
        ScheduleLineDate     : Date;         // スケジュールライン納期
        DeliveryScheduleQty  : Decimal(13, 3); // スケジュールライン数量
  };

}
