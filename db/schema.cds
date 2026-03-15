using { cuid, managed, sap.common.CodeList } from '@sap/cds/common';

namespace com.example.bp;

// ================================================================
// 外部CAPプロジェクトのマスタ（HANAシノニム経由）
// @cds.persistence.exists : CAPがDDLを生成せず既存HANAオブジェクトを参照
// シノニム実体は別CAPプロジェクトのHANAスキーマに存在する
// ================================================================
context master {

  // --------------------------------------------------------------
  // 得意先マスタ
  // シノニム名: CUSTOMER_MASTER (外部CAPプロジェクトのテーブルを参照)
  // 検索条件: CustomerID + SalesOrganization + DistributionChannel + Division
  //           → CDS Viewのレコード単位で販売エリアの組み合わせが変動する
  // --------------------------------------------------------------
  entity CustomerMaster {
    key CustomerID          : String(10);   // 得意先コード
    key SalesOrganization   : String(4);    // 販売組織
    key DistributionChannel : String(2);    // 流通チャネル
    key Division            : String(2);    // 製品部門
        CustomerName        : String(80);   // 得意先名
        CustomerGroup       : String(4);    // 得意先グループ
        Currency            : String(5);    // 通貨
        PaymentTerms        : String(4);    // 支払条件
        CreditLimit         : Decimal(15, 2); // 与信限度額
        SalesDistrict       : String(6);    // 販売地区
  };

  // --------------------------------------------------------------
  // 品目マスタ
  // シノニム名: MATERIAL_MASTER
  // 検索条件: MaterialCode → CDS Viewの明細レコード単位で変動する
  // --------------------------------------------------------------
  entity MaterialMaster {
    key MaterialCode      : String(18);   // 品目コード
        MaterialName      : String(40);   // 品目名
        MaterialGroup     : String(9);    // 品目グループ
        BaseUnit          : String(3);    // 基本単位
        ProductHierarchy  : String(18);   // 製品階層
        TaxClassification : String(1);    // 課税区分
        WeightUnit        : String(3);    // 重量単位
        GrossWeight       : Decimal(13, 3); // 総重量
  };

  // --------------------------------------------------------------
  // プラントマスタ
  // シノニム名: PLANT_MASTER
  // 検索条件: Plant → CDS Viewの明細レコード単位で変動する
  // --------------------------------------------------------------
  entity PlantMaster {
    key Plant           : String(4);    // プラント
        PlantName       : String(30);   // プラント名
        CompanyCode     : String(4);    // 会社コード
        Country         : String(3);    // 国
        FactoryCalendar : String(2);    // 工場カレンダー
  };

}

// ================================================================
// 受注ヘッダ（SalesDocItemView の起点テーブル）
// マスタ補完ビューの JOIN 起点となる受注データを保持する
// SalesDocHeader/SalesDocItem/SalesDocDetail とは独立した別エンティティ
// ================================================================
entity OrderHeader {
  key SalesDocument       : String(10);     // 受注番号
  key SalesDocumentItem   : String(6);      // 明細番号
      SalesOrganization   : String(4);      // 販売組織
      DistributionChannel : String(2);      // 流通チャネル
      Division            : String(2);      // 製品部門
      CustomerID          : String(10);     // 得意先コード
      MaterialCode        : String(18);     // 品目コード
      Plant               : String(4);      // プラント
      OrderDate           : Date;           // 受注日
      OrderQuantity       : Decimal(13, 3); // 受注数量
      OrderQuantityUnit   : String(3);      // 数量単位
      NetAmount           : Decimal(15, 2); // 正味金額
      Currency            : String(5);      // 通貨
      StorageLocation     : String(4);      // 保管場所
      PricingDate         : Date;           // 価格決定日
}

// ================================================================
// 売上伝票エンティティ（ローカル生成・保持）
// S4 CDS View から取得したデータ＋マスタ補完値を格納する
// ================================================================

// ---------------------------------------------------------------
// 売上伝票ヘッダ
// ---------------------------------------------------------------
entity SalesDocHeader : managed {
  key SalesDocument       : String(10);     // 売上伝票番号
      SalesOrganization   : String(4);      // 販売組織
      DistributionChannel : String(2);      // 流通チャネル
      Division            : String(2);      // 製品部門
      SalesDocumentDate   : Date;           // 伝票日付
      SalesDocumentType   : String(4);      // 伝票種別
      CustomerID          : String(10);     // 得意先コード
      CustomerName        : String(80);     // 得意先名 (CustomerMasterから取得)
      CustomerGroup       : String(4);      // 得意先グループ (CustomerMasterから取得)
      Currency            : String(5);      // 通貨
      TotalNetAmount      : Decimal(15, 2); // 合計正味金額
      Status              : String(2) default '01'; // 01:処理中 02:完了 09:エラー
      ErrorMessage        : String(255);    // エラーメッセージ
      items               : Composition of many SalesDocItem
                              on items.SalesDocument = SalesDocument;
}

// ---------------------------------------------------------------
// 売上伝票明細
// ---------------------------------------------------------------
entity SalesDocItem : managed {
  key SalesDocument       : String(10);     // 売上伝票番号
  key SalesDocumentItem   : String(6);      // 明細番号
      MaterialCode        : String(18);     // 品目コード
      MaterialName        : String(40);     // 品目名 (MaterialMasterから取得)
      MaterialGroup       : String(9);      // 品目グループ (MaterialMasterから取得)
      OrderQuantity       : Decimal(13, 3); // 注文数量
      OrderQuantityUnit   : String(3);      // 数量単位
      NetAmount           : Decimal(15, 2); // 正味金額
      Currency            : String(5);      // 通貨
      Plant               : String(4);      // プラント
      PlantName           : String(30);     // プラント名 (PlantMasterから取得)
      CompanyCode         : String(4);      // 会社コード (PlantMasterから取得)
      StorageLocation     : String(4);      // 保管場所
      PricingDate         : Date;           // 価格決定日
      details             : Composition of many SalesDocDetail
                              on  details.SalesDocument    = SalesDocument
                              and details.SalesDocumentItem = SalesDocumentItem;
}

// ---------------------------------------------------------------
// 売上伝票詳細（条件明細・スケジュールライン等）
// ---------------------------------------------------------------
entity SalesDocDetail : managed {
  key SalesDocument       : String(10);     // 売上伝票番号
  key SalesDocumentItem   : String(6);      // 明細番号
  key SequentialNumber    : String(3);      // 連番（詳細番号）
      DetailCategory      : String(2);      // 詳細区分 (PR=価格条件, SL=スケジュールライン)
      DetailText          : String(255);    // 詳細テキスト
      DetailAmount        : Decimal(15, 2); // 詳細金額
      ConditionType       : String(4);      // 条件タイプ
      ScheduleLineDate    : Date;           // スケジュールライン納期
      DeliveryScheduleQty : Decimal(13, 3); // スケジュールライン数量
      Currency            : String(5);      // 通貨
}

// ---------------------------------------------------------------
// ローカルキャッシュ: 取引先マスタ
// S/4HANAから取得した取引先情報をローカルに保持する
// ---------------------------------------------------------------
entity BusinessPartners : managed {
  key ID                      : UUID;
      businessPartnerID       : String(10);  // S/4HANA の BusinessPartner キー
      fullName                : String(81);
      firstName               : String(40);
      lastName                : String(40);
      category                : String(1);   // '1'=個人, '2'=法人, '3'=グループ
      isBlocked               : Boolean default false;
      isMarkedForDeletion     : Boolean default false;
      addresses               : Composition of many Addresses
                                  on addresses.businessPartner = $self;
}

// ---------------------------------------------------------------
// ローカルキャッシュ: 住所
// ---------------------------------------------------------------
entity Addresses : managed {
  key ID                  : UUID;
      businessPartner     : Association to BusinessPartners;
      addressID           : String(10);
      country             : String(3);
      region              : String(3);
      postalCode          : String(10);
      cityName            : String(40);
      streetName          : String(60);
      houseNumber         : String(10);
      isDefault           : Boolean default false;
      emailAddresses      : Composition of many EmailAddresses
                              on emailAddresses.address = $self;
      phoneNumbers        : Composition of many PhoneNumbers
                              on phoneNumbers.address = $self;
}

// ---------------------------------------------------------------
// ローカルキャッシュ: メールアドレス
// ---------------------------------------------------------------
entity EmailAddresses : managed {
  key ID           : UUID;
      address      : Association to Addresses;
      email        : String(241);
      isDefault    : Boolean default false;
}

// ---------------------------------------------------------------
// ローカルキャッシュ: 電話番号
// ---------------------------------------------------------------
entity PhoneNumbers : managed {
  key ID             : UUID;
      address        : Association to Addresses;
      phoneNumber    : String(30);
      extension      : String(10);
      isDefault      : Boolean default false;
}
