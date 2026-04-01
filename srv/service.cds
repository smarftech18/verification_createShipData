using {com.example.bp as db} from '../db/schema';
using {API_BUSINESS_PARTNER as ext} from './external/API_BUSINESS_PARTNER';
using {ZC_SALESDOCUMENT_SERVICE as s4} from './external/ZC_SALESDOCUMENT_SERVICE';

// ---------------------------------------------------------------
// BusinessPartnerService
// 外部 S/4HANA の取引先 API をプロキシし、
// ローカルキャッシュとのハイブリッドアクセスを提供する
// ---------------------------------------------------------------
service BusinessPartnerService @(path: '/api/business-partners') {

  // MARk : エンティティ
  // ----------------------------------------------------------
  // ローカルキャッシュエンティティ（読み書き可）
  // ----------------------------------------------------------
  entity BusinessPartners   as projection on db.BusinessPartners
    actions {
      // S/4HANA から最新情報をフェッチしてキャッシュを更新する
      action syncFromS4() returns String;
    };

  entity Addresses          as projection on db.Addresses;
  entity EmailAddresses     as projection on db.EmailAddresses;
  entity PhoneNumbers       as projection on db.PhoneNumbers;

  // ----------------------------------------------------------
  // 外部サービスへのパススルー（読み取り専用プロジェクション）
  // S/4HANA の A_BusinessPartner をそのまま公開する
  // ----------------------------------------------------------
  @readonly
  entity S4BusinessPartners as
    projection on ext.A_BusinessPartner {
      BusinessPartner,
      BusinessPartnerFullName,
      BusinessPartnerCategory,
      FirstName,
      LastName,
      OrganizationBPName1,
      BusinessPartnerIsBlocked,
      CreationDate,
      LastChangeDate,
      to_BusinessPartnerAddress
    };

  @readonly
  entity S4Addresses        as
    projection on ext.A_BusinessPartnerAddress {
      BusinessPartner,
      AddressID,
      Country,
      Region,
      PostalCode,
      CityName,
      StreetName,
      HouseNumber,
      IsDefaultAddress,
      to_EmailAddress,
      to_PhoneNumber
    };

  @readonly
  entity S4EmailAddresses   as projection on ext.A_AddressEmailAddress;

  @readonly
  entity S4PhoneNumbers     as projection on ext.A_AddressPhoneNumber;

  // MARk : アクション
  
  // ----------------------------------------------------------
  // カスタムアクション
  // ----------------------------------------------------------

  // 指定した取引先IDで S/4HANA を検索してローカルDBへ同期する
  action   importBusinessPartner(businessPartnerID: String(10) @mandatory,
                                 includeAddresses: Boolean default true) returns {
    success         : Boolean;
    message         : String;
    recordsImported : Integer;
  };

  // ローカルキャッシュの統計情報を返す
  function getCacheStats()                                               returns {
    totalPartners  : Integer;
    totalAddresses : Integer;
    lastSyncDate   : DateTime;
  };
}


// ================================================================
// SalesDocumentService
// ① S4 CDS View (ZC_SALESDOCUMENT) を取込元として売上伝票を生成する
// ② 生成時にシノニム経由のマスタを LEFT JOIN で補完する
// ③ イベントハンドラ (SalesDocumentHandler.java) がビジネスロジックを担う
// ================================================================
service SalesDocumentService @(path: '/api/sales-documents') {

  // ----------------------------------------------------------
  // 売上伝票ローカルエンティティ（読み書き可）
  // ----------------------------------------------------------
  entity SalesDocHeaders  as projection on db.SalesDocHeader;
  entity SalesDocItems    as projection on db.SalesDocItem;
  entity SalesDocDetails  as projection on db.SalesDocDetail;

  // ----------------------------------------------------------
  // 売上伝票明細ビュー（マスタ LEFT JOIN）
  //
  // 用途:
  //   - createSalesDocuments アクションがマスタ補完済みデータを一括参照する際に使用
  //   - ZcSalesDocument の各項目値を検索条件としてマスタ値を取得する
  //
  // 起点:
  //   - db.OrderHeader（受注ヘッダ）
  //   ※ SalesDocHeader/SalesDocItem/SalesDocDetail（作成対象エンティティ）は結合しない
  //
  // JOIN 対象:
  //   - OrderHeader × CustomerMaster … LEFT JOIN（得意先マスタ）
  //   - OrderHeader × MaterialMaster … LEFT JOIN（品目マスタ）
  //   - OrderHeader × PlantMaster    … LEFT JOIN（プラントマスタ）
  // ----------------------------------------------------------
  @readonly
  view SalesDocItemView as
    select from db.OrderHeader as oh
    left join db.master.CustomerMaster as cm
      on  cm.CustomerID          = oh.CustomerID
      and cm.SalesOrganization   = oh.SalesOrganization
      and cm.DistributionChannel = oh.DistributionChannel
      and cm.Division            = oh.Division
    left join db.master.MaterialMaster as mm
      on mm.MaterialCode = oh.MaterialCode
    left join db.master.PlantMaster as pm
      on pm.Plant = oh.Plant
    {
          // キー
      key oh.SalesDocument,
      key oh.SalesDocumentItem,

          // 受注ヘッダ項目（マスタ JOIN キー）
          oh.SalesOrganization,
          oh.DistributionChannel,
          oh.Division,
          oh.CustomerID,
          oh.MaterialCode,
          oh.Plant,

          // 得意先マスタ補完
          cm.CustomerName,
          cm.CustomerGroup,
          cm.Currency as CustomerCurrency,
          cm.PaymentTerms,
          cm.SalesDistrict,

          // 品目マスタ補完
          mm.MaterialName,
          mm.MaterialGroup,
          mm.BaseUnit,
          mm.ProductHierarchy,
          mm.TaxClassification,

          // プラントマスタ補完
          pm.PlantName,
          pm.CompanyCode,
          pm.FactoryCalendar
    };

  // ----------------------------------------------------------
  // S4 CDS View パススルー（読み取り専用）
  // イベントハンドラが外部 OData エンドポイントへ委譲する
  // ----------------------------------------------------------
  @readonly
  entity S4SalesDocuments as projection on s4.ZcSalesDocument;

  // ----------------------------------------------------------
  // アクション / ファンクション
  // ----------------------------------------------------------

  // 売上伝票生成アクション
  // S4 CDS View データを取込み、マスタを補完して売上伝票3テーブルを生成する
  action   createSalesDocuments(salesDocument: String(10), // 対象伝票番号（未指定で全件）
                                forceUpdate: Boolean default false // true=既存データを上書き
  )                                                                      returns {
    success        : Boolean;
    message        : String;
    headersCreated : Integer;
    itemsCreated   : Integer;
    detailsCreated : Integer;
    errorCount     : Integer;
  };

  // 処理ステータス照会
  function getSalesDocStatus(salesDocument: String(10) @mandatory
  )                                                                      returns {
    salesDocument : String(10);
    status        : String(2);
    statusText    : String(20);
    lastUpdated   : DateTime;
    errorMessage  : String(255);
  };
}
