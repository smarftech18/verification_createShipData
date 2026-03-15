
DROP VIEW IF EXISTS SalesDocumentService_S4SalesDocuments;
DROP VIEW IF EXISTS SalesDocumentService_SalesDocItemView;
DROP VIEW IF EXISTS SalesDocumentService_SalesDocDetails;
DROP VIEW IF EXISTS SalesDocumentService_SalesDocItems;
DROP VIEW IF EXISTS SalesDocumentService_SalesDocHeaders;
DROP VIEW IF EXISTS BusinessPartnerService_S4PhoneNumbers;
DROP VIEW IF EXISTS BusinessPartnerService_S4EmailAddresses;
DROP VIEW IF EXISTS BusinessPartnerService_S4Addresses;
DROP VIEW IF EXISTS BusinessPartnerService_S4BusinessPartners;
DROP VIEW IF EXISTS BusinessPartnerService_PhoneNumbers;
DROP VIEW IF EXISTS BusinessPartnerService_EmailAddresses;
DROP VIEW IF EXISTS BusinessPartnerService_Addresses;
DROP VIEW IF EXISTS BusinessPartnerService_BusinessPartners;
DROP TABLE IF EXISTS cds_outbox_Messages;
DROP TABLE IF EXISTS ZC_SALESDOCUMENT_SERVICE_ZcSalesDocument;
DROP TABLE IF EXISTS com_example_bp_master_PlantMaster;
DROP TABLE IF EXISTS com_example_bp_master_MaterialMaster;
DROP TABLE IF EXISTS com_example_bp_master_CustomerMaster;
DROP TABLE IF EXISTS com_example_bp_OrderHeader;
DROP TABLE IF EXISTS com_example_bp_SalesDocDetail;
DROP TABLE IF EXISTS com_example_bp_SalesDocItem;
DROP TABLE IF EXISTS com_example_bp_SalesDocHeader;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_Supplier;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_CustomerSalesArea;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_Customer;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_AddressPhoneNumber;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_AddressEmailAddress;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_BusinessPartnerAddress;
DROP TABLE IF EXISTS API_BUSINESS_PARTNER_A_BusinessPartner;
DROP TABLE IF EXISTS com_example_bp_PhoneNumbers;
DROP TABLE IF EXISTS com_example_bp_EmailAddresses;
DROP TABLE IF EXISTS com_example_bp_Addresses;
DROP TABLE IF EXISTS com_example_bp_BusinessPartners;

CREATE TABLE com_example_bp_BusinessPartners (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  ID NVARCHAR(36) NOT NULL,
  businessPartnerID NVARCHAR(10),
  fullName NVARCHAR(81),
  firstName NVARCHAR(40),
  lastName NVARCHAR(40),
  category NVARCHAR(1),
  isBlocked BOOLEAN DEFAULT FALSE,
  isMarkedForDeletion BOOLEAN DEFAULT FALSE,
  PRIMARY KEY(ID)
);

CREATE TABLE com_example_bp_Addresses (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  ID NVARCHAR(36) NOT NULL,
  businessPartner_ID NVARCHAR(36),
  addressID NVARCHAR(10),
  country NVARCHAR(3),
  region NVARCHAR(3),
  postalCode NVARCHAR(10),
  cityName NVARCHAR(40),
  streetName NVARCHAR(60),
  houseNumber NVARCHAR(10),
  isDefault BOOLEAN DEFAULT FALSE,
  PRIMARY KEY(ID)
);

CREATE TABLE com_example_bp_EmailAddresses (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  ID NVARCHAR(36) NOT NULL,
  address_ID NVARCHAR(36),
  email NVARCHAR(241),
  isDefault BOOLEAN DEFAULT FALSE,
  PRIMARY KEY(ID)
);

CREATE TABLE com_example_bp_PhoneNumbers (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  ID NVARCHAR(36) NOT NULL,
  address_ID NVARCHAR(36),
  phoneNumber NVARCHAR(30),
  extension NVARCHAR(10),
  isDefault BOOLEAN DEFAULT FALSE,
  PRIMARY KEY(ID)
);

CREATE TABLE API_BUSINESS_PARTNER_A_BusinessPartner (
  BusinessPartner NVARCHAR(10) NOT NULL,
  Customer NVARCHAR(10),
  Supplier NVARCHAR(10),
  AcademicTitle NVARCHAR(4),
  AuthorizationGroup NVARCHAR(4),
  BusinessPartnerCategory NVARCHAR(1),
  BusinessPartnerFullName NVARCHAR(81),
  BusinessPartnerGrouping NVARCHAR(4),
  BusinessPartnerName NVARCHAR(81),
  BusinessPartnerUUID NVARCHAR(36),
  CorrespondenceLanguage NVARCHAR(2),
  CreatedByUser NVARCHAR(12),
  CreationDate DATE,
  CreationTime TIME,
  FirstName NVARCHAR(40),
  FormOfAddress NVARCHAR(4),
  Industry NVARCHAR(10),
  IsNaturalPerson NVARCHAR(1),
  Language NVARCHAR(2),
  LastChangeDate DATE,
  LastChangeTime TIME,
  LastChangedByUser NVARCHAR(12),
  LastName NVARCHAR(40),
  LegalForm NVARCHAR(2),
  OrganizationBPName1 NVARCHAR(40),
  OrganizationBPName2 NVARCHAR(40),
  OrganizationBPName3 NVARCHAR(40),
  OrganizationBPName4 NVARCHAR(40),
  OrganizationFoundationDate DATE,
  OrganizationLiquidationDate DATE,
  SearchTerm1 NVARCHAR(20),
  SearchTerm2 NVARCHAR(20),
  BirthDate DATE,
  BusinessPartnerBirthplaceName NVARCHAR(40),
  BusinessPartnerDeathDate DATE,
  BusinessPartnerIsBlocked BOOLEAN,
  BusinessPartnerType NVARCHAR(4),
  GroupBusinessPartnerName1 NVARCHAR(40),
  GroupBusinessPartnerName2 NVARCHAR(40),
  MiddleName NVARCHAR(40),
  NameCountry NVARCHAR(3),
  PersonFullName NVARCHAR(81),
  PersonNumber NVARCHAR(10),
  IsMarkedForArchiving BOOLEAN,
  BusinessPartnerIDByExtSystem NVARCHAR(20),
  TradingPartner NVARCHAR(6),
  PRIMARY KEY(BusinessPartner)
);

CREATE TABLE API_BUSINESS_PARTNER_A_BusinessPartnerAddress (
  BusinessPartner NVARCHAR(10) NOT NULL,
  AddressID NVARCHAR(10) NOT NULL,
  ValidityStartDate TIMESTAMP(0),
  ValidityEndDate TIMESTAMP(0),
  AuthorizationGroup NVARCHAR(4),
  AddressUUID NVARCHAR(36),
  AdditionalStreetPrefixName NVARCHAR(40),
  AdditionalStreetSuffixName NVARCHAR(40),
  AddressTimeZone NVARCHAR(6),
  CareOfName NVARCHAR(40),
  CityCode NVARCHAR(12),
  CityName NVARCHAR(40),
  CompanyPostalCode NVARCHAR(10),
  Country NVARCHAR(3),
  County NVARCHAR(40),
  DeliveryServiceNumber NVARCHAR(10),
  DeliveryServiceTypeCode NVARCHAR(4),
  District NVARCHAR(40),
  FormOfAddress NVARCHAR(4),
  FullName NVARCHAR(80),
  HomeCityName NVARCHAR(40),
  HouseNumber NVARCHAR(10),
  HouseNumberSupplementText NVARCHAR(10),
  Language NVARCHAR(2),
  POBox NVARCHAR(10),
  POBoxIsWithoutNumber BOOLEAN,
  POBoxPostalCode NVARCHAR(10),
  Person NVARCHAR(10),
  PostalCode NVARCHAR(10),
  Region NVARCHAR(3),
  StreetName NVARCHAR(60),
  StreetPrefixName NVARCHAR(40),
  StreetSuffixName NVARCHAR(40),
  TaxJurisdiction NVARCHAR(15),
  TransportZone NVARCHAR(10),
  IsDefaultAddress BOOLEAN,
  PRIMARY KEY(BusinessPartner, AddressID)
);

CREATE TABLE API_BUSINESS_PARTNER_A_AddressEmailAddress (
  AddressID NVARCHAR(10) NOT NULL,
  Person NVARCHAR(10) NOT NULL,
  OrdinalNumber NVARCHAR(3) NOT NULL,
  IsDefaultEmailAddress BOOLEAN,
  EmailAddress NVARCHAR(241),
  SearchEmailAddress NVARCHAR(20),
  AddressCommunicationRemarkText NVARCHAR(50),
  PRIMARY KEY(AddressID, Person, OrdinalNumber)
);

CREATE TABLE API_BUSINESS_PARTNER_A_AddressPhoneNumber (
  AddressID NVARCHAR(10) NOT NULL,
  Person NVARCHAR(10) NOT NULL,
  OrdinalNumber NVARCHAR(3) NOT NULL,
  DestinationLocationCountry NVARCHAR(3),
  Connection NVARCHAR(30),
  IsDefaultPhoneNumber BOOLEAN,
  IsDefaultSmSNumber BOOLEAN,
  PhoneNumber NVARCHAR(30),
  PhoneNumberType NVARCHAR(1),
  PhoneExtensionNumber NVARCHAR(10),
  InternationalPhoneNumber NVARCHAR(30),
  AddressCommunicationRemarkText NVARCHAR(50),
  PRIMARY KEY(AddressID, Person, OrdinalNumber)
);

CREATE TABLE API_BUSINESS_PARTNER_A_Customer (
  Customer NVARCHAR(10) NOT NULL,
  AuthorizationGroup NVARCHAR(4),
  BillingIsBlockedForCustomer NVARCHAR(2),
  CreatedByUser NVARCHAR(12),
  CreationDate DATE,
  CustomerAccountGroup NVARCHAR(4),
  CustomerClassification NVARCHAR(2),
  CustomerFullName NVARCHAR(220),
  CustomerName NVARCHAR(220),
  DeliveryIsBlocked NVARCHAR(2),
  FreeDefinedAttribute01 NVARCHAR(2),
  NFPartnerIsNaturalPerson NVARCHAR(1),
  OrderIsBlockedForCustomer NVARCHAR(2),
  PostingIsBlocked BOOLEAN,
  Supplier NVARCHAR(10),
  CustomerCorporateGroup NVARCHAR(10),
  FiscalAddress NVARCHAR(10),
  Industry NVARCHAR(4),
  IndustryCode1 NVARCHAR(10),
  InternationalLocationNumber1 NVARCHAR(7),
  InternationalLocationNumber2 NVARCHAR(5),
  IsMarkedForDeletion BOOLEAN,
  PRIMARY KEY(Customer)
);

CREATE TABLE API_BUSINESS_PARTNER_A_CustomerSalesArea (
  Customer NVARCHAR(10) NOT NULL,
  SalesOrganization NVARCHAR(4) NOT NULL,
  DistributionChannel NVARCHAR(2) NOT NULL,
  Division NVARCHAR(2) NOT NULL,
  AccountByCustomer NVARCHAR(12),
  AuthorizationGroup NVARCHAR(4),
  BillingIsBlockedForCustomer NVARCHAR(2),
  CompleteDeliveryIsDefined BOOLEAN,
  CreditControlArea NVARCHAR(4),
  Currency NVARCHAR(5),
  CustomerABCClassification NVARCHAR(2),
  CustomerAccountAssignmentGroup NVARCHAR(2),
  CustomerGroup NVARCHAR(2),
  CustomerPaymentTerms NVARCHAR(4),
  CustomerPriceGroup NVARCHAR(2),
  CustomerPricingProcedure NVARCHAR(2),
  DeliveryIsBlockedForCustomer NVARCHAR(2),
  IncotermsClassification NVARCHAR(3),
  IncotermsTransferLocation NVARCHAR(28),
  IncotermsLocation1 NVARCHAR(70),
  IncotermsLocation2 NVARCHAR(70),
  IncotermsVersion NVARCHAR(4),
  ItemOrderProbabilityInPercent NVARCHAR(3),
  ManualInvoiceMaintIsRelevant BOOLEAN,
  MaxNmbrOfPartialDelivery DECIMAL(1, 0),
  OrderCombinationIsAllowed BOOLEAN,
  OrderIsBlockedForCustomer NVARCHAR(2),
  OverdelivTolrtdLmtRatioInPct DECIMAL(3, 1),
  PartialDeliveryIsAllowed NVARCHAR(1),
  PriceListType NVARCHAR(2),
  ProductTaxClassification1 NVARCHAR(1),
  SalesGroup NVARCHAR(3),
  SalesItemProposal NVARCHAR(10),
  SalesOffice NVARCHAR(4),
  ShippingCondition NVARCHAR(2),
  SlsDocIsRlvtForProofOfDlvry BOOLEAN,
  SlsUnlmtdOvrdelivIsAllwd BOOLEAN,
  SupplyingPlant NVARCHAR(4),
  SalesDistrict NVARCHAR(6),
  UnderdelivTolrtdLmtRatioInPct DECIMAL(3, 1),
  InvoiceDate NVARCHAR(2),
  PRIMARY KEY(Customer, SalesOrganization, DistributionChannel, Division)
);

CREATE TABLE API_BUSINESS_PARTNER_A_Supplier (
  Supplier NVARCHAR(10) NOT NULL,
  AuthorizationGroup NVARCHAR(4),
  CreatedByUser NVARCHAR(12),
  CreationDate DATE,
  Customer NVARCHAR(10),
  IsMarkedForDeletion BOOLEAN,
  IsNaturalPerson NVARCHAR(1),
  PaymentIsBlockedForSupplier BOOLEAN,
  PostingIsBlocked BOOLEAN,
  PurchasingIsBlocked BOOLEAN,
  SupplierAccountGroup NVARCHAR(4),
  SupplierFullName NVARCHAR(220),
  SupplierName NVARCHAR(220),
  VATRegistration NVARCHAR(20),
  BirthDate DATE,
  ConcatenatedInternationalLocNo NVARCHAR(14),
  DeletionIndicator BOOLEAN,
  FiscalAddress NVARCHAR(10),
  Industry NVARCHAR(4),
  InternationalLocationNumber1 NVARCHAR(7),
  InternationalLocationNumber2 NVARCHAR(5),
  IsNaturalPerson2 NVARCHAR(1),
  ResponsibleType NVARCHAR(2),
  SuplrQltyInProcmtCertfnValidTo DATE,
  SuplrQltyInProcmtIsActive BOOLEAN,
  SuplrQltyMgmtSystemValidTo DATE,
  SupplierCorporateGroup NVARCHAR(10),
  SupplierProcurementBlock NVARCHAR(2),
  TaxNumber1 NVARCHAR(16),
  TaxNumber2 NVARCHAR(11),
  TaxNumber3 NVARCHAR(18),
  TaxNumber4 NVARCHAR(18),
  TaxNumber5 NVARCHAR(60),
  TaxNumberResponsible NVARCHAR(30),
  TaxNumberType NVARCHAR(2),
  SuplrProofOfDelivRlvtCode NVARCHAR(1),
  BR_TaxIsSplit BOOLEAN,
  PRIMARY KEY(Supplier)
);

CREATE TABLE com_example_bp_SalesDocHeader (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  SalesDocument NVARCHAR(10) NOT NULL,
  SalesOrganization NVARCHAR(4),
  DistributionChannel NVARCHAR(2),
  Division NVARCHAR(2),
  SalesDocumentDate DATE,
  SalesDocumentType NVARCHAR(4),
  CustomerID NVARCHAR(10),
  CustomerName NVARCHAR(80),
  CustomerGroup NVARCHAR(4),
  Currency NVARCHAR(5),
  TotalNetAmount DECIMAL(15, 2),
  Status NVARCHAR(2) DEFAULT '01',
  ErrorMessage NVARCHAR(255),
  PRIMARY KEY(SalesDocument)
);

CREATE TABLE com_example_bp_SalesDocItem (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  SalesDocument NVARCHAR(10) NOT NULL,
  SalesDocumentItem NVARCHAR(6) NOT NULL,
  MaterialCode NVARCHAR(18),
  MaterialName NVARCHAR(40),
  MaterialGroup NVARCHAR(9),
  OrderQuantity DECIMAL(13, 3),
  OrderQuantityUnit NVARCHAR(3),
  NetAmount DECIMAL(15, 2),
  Currency NVARCHAR(5),
  Plant NVARCHAR(4),
  PlantName NVARCHAR(30),
  CompanyCode NVARCHAR(4),
  StorageLocation NVARCHAR(4),
  PricingDate DATE,
  PRIMARY KEY(SalesDocument, SalesDocumentItem)
);

CREATE TABLE com_example_bp_SalesDocDetail (
  createdAt TIMESTAMP(7),
  createdBy NVARCHAR(255),
  modifiedAt TIMESTAMP(7),
  modifiedBy NVARCHAR(255),
  SalesDocument NVARCHAR(10) NOT NULL,
  SalesDocumentItem NVARCHAR(6) NOT NULL,
  SequentialNumber NVARCHAR(3) NOT NULL,
  DetailCategory NVARCHAR(2),
  DetailText NVARCHAR(255),
  DetailAmount DECIMAL(15, 2),
  ConditionType NVARCHAR(4),
  ScheduleLineDate DATE,
  DeliveryScheduleQty DECIMAL(13, 3),
  Currency NVARCHAR(5),
  PRIMARY KEY(SalesDocument, SalesDocumentItem, SequentialNumber)
);

CREATE TABLE com_example_bp_OrderHeader (
  SalesDocument NVARCHAR(10) NOT NULL,
  SalesDocumentItem NVARCHAR(6) NOT NULL,
  SalesOrganization NVARCHAR(4),
  DistributionChannel NVARCHAR(2),
  Division NVARCHAR(2),
  CustomerID NVARCHAR(10),
  MaterialCode NVARCHAR(18),
  Plant NVARCHAR(4),
  OrderDate DATE,
  OrderQuantity DECIMAL(13, 3),
  OrderQuantityUnit NVARCHAR(3),
  NetAmount DECIMAL(15, 2),
  Currency NVARCHAR(5),
  StorageLocation NVARCHAR(4),
  PricingDate DATE,
  PRIMARY KEY(SalesDocument, SalesDocumentItem)
);

CREATE TABLE com_example_bp_master_CustomerMaster (
  CustomerID NVARCHAR(10) NOT NULL,
  SalesOrganization NVARCHAR(4) NOT NULL,
  DistributionChannel NVARCHAR(2) NOT NULL,
  Division NVARCHAR(2) NOT NULL,
  CustomerName NVARCHAR(80),
  CustomerGroup NVARCHAR(4),
  Currency NVARCHAR(5),
  PaymentTerms NVARCHAR(4),
  CreditLimit DECIMAL(15, 2),
  SalesDistrict NVARCHAR(6),
  PRIMARY KEY(CustomerID, SalesOrganization, DistributionChannel, Division)
);

CREATE TABLE com_example_bp_master_MaterialMaster (
  MaterialCode NVARCHAR(18) NOT NULL,
  MaterialName NVARCHAR(40),
  MaterialGroup NVARCHAR(9),
  BaseUnit NVARCHAR(3),
  ProductHierarchy NVARCHAR(18),
  TaxClassification NVARCHAR(1),
  WeightUnit NVARCHAR(3),
  GrossWeight DECIMAL(13, 3),
  PRIMARY KEY(MaterialCode)
);

CREATE TABLE com_example_bp_master_PlantMaster (
  Plant NVARCHAR(4) NOT NULL,
  PlantName NVARCHAR(30),
  CompanyCode NVARCHAR(4),
  Country NVARCHAR(3),
  FactoryCalendar NVARCHAR(2),
  PRIMARY KEY(Plant)
);

CREATE TABLE ZC_SALESDOCUMENT_SERVICE_ZcSalesDocument (
  SalesDocument NVARCHAR(10) NOT NULL,
  SalesDocumentItem NVARCHAR(6) NOT NULL,
  SequentialNumber NVARCHAR(3) NOT NULL,
  SalesOrganization NVARCHAR(4),
  DistributionChannel NVARCHAR(2),
  Division NVARCHAR(2),
  SalesDocumentDate DATE,
  SalesDocumentType NVARCHAR(4),
  CustomerID NVARCHAR(10),
  MaterialCode NVARCHAR(18),
  OrderQuantity DECIMAL(13, 3),
  OrderQuantityUnit NVARCHAR(3),
  NetAmount DECIMAL(15, 2),
  Currency NVARCHAR(5),
  Plant NVARCHAR(4),
  StorageLocation NVARCHAR(4),
  PricingDate DATE,
  DetailCategory NVARCHAR(2),
  DetailText NVARCHAR(255),
  DetailAmount DECIMAL(15, 2),
  ConditionType NVARCHAR(4),
  ScheduleLineDate DATE,
  DeliveryScheduleQty DECIMAL(13, 3),
  PRIMARY KEY(SalesDocument, SalesDocumentItem, SequentialNumber)
);

CREATE TABLE cds_outbox_Messages (
  ID NVARCHAR(36) NOT NULL,
  timestamp TIMESTAMP(7),
  target NVARCHAR(255),
  msg NCLOB,
  attempts INTEGER DEFAULT 0,
  "PARTITION" INTEGER DEFAULT 0,
  lastError NCLOB,
  lastAttemptTimestamp TIMESTAMP(7),
  status NVARCHAR(23),
  task NVARCHAR(255),
  appid NVARCHAR(255),
  PRIMARY KEY(ID)
);

CREATE VIEW BusinessPartnerService_BusinessPartners AS SELECT
  BusinessPartners_0.createdAt,
  BusinessPartners_0.createdBy,
  BusinessPartners_0.modifiedAt,
  BusinessPartners_0.modifiedBy,
  BusinessPartners_0.ID,
  BusinessPartners_0.businessPartnerID,
  BusinessPartners_0.fullName,
  BusinessPartners_0.firstName,
  BusinessPartners_0.lastName,
  BusinessPartners_0.category,
  BusinessPartners_0.isBlocked,
  BusinessPartners_0.isMarkedForDeletion
FROM com_example_bp_BusinessPartners AS BusinessPartners_0;

CREATE VIEW BusinessPartnerService_Addresses AS SELECT
  Addresses_0.createdAt,
  Addresses_0.createdBy,
  Addresses_0.modifiedAt,
  Addresses_0.modifiedBy,
  Addresses_0.ID,
  Addresses_0.businessPartner_ID,
  Addresses_0.addressID,
  Addresses_0.country,
  Addresses_0.region,
  Addresses_0.postalCode,
  Addresses_0.cityName,
  Addresses_0.streetName,
  Addresses_0.houseNumber,
  Addresses_0.isDefault
FROM com_example_bp_Addresses AS Addresses_0;

CREATE VIEW BusinessPartnerService_EmailAddresses AS SELECT
  EmailAddresses_0.createdAt,
  EmailAddresses_0.createdBy,
  EmailAddresses_0.modifiedAt,
  EmailAddresses_0.modifiedBy,
  EmailAddresses_0.ID,
  EmailAddresses_0.address_ID,
  EmailAddresses_0.email,
  EmailAddresses_0.isDefault
FROM com_example_bp_EmailAddresses AS EmailAddresses_0;

CREATE VIEW BusinessPartnerService_PhoneNumbers AS SELECT
  PhoneNumbers_0.createdAt,
  PhoneNumbers_0.createdBy,
  PhoneNumbers_0.modifiedAt,
  PhoneNumbers_0.modifiedBy,
  PhoneNumbers_0.ID,
  PhoneNumbers_0.address_ID,
  PhoneNumbers_0.phoneNumber,
  PhoneNumbers_0.extension,
  PhoneNumbers_0.isDefault
FROM com_example_bp_PhoneNumbers AS PhoneNumbers_0;

CREATE VIEW BusinessPartnerService_S4BusinessPartners AS SELECT
  A_BusinessPartner_0.BusinessPartner,
  A_BusinessPartner_0.BusinessPartnerFullName,
  A_BusinessPartner_0.BusinessPartnerCategory,
  A_BusinessPartner_0.FirstName,
  A_BusinessPartner_0.LastName,
  A_BusinessPartner_0.OrganizationBPName1,
  A_BusinessPartner_0.BusinessPartnerIsBlocked,
  A_BusinessPartner_0.CreationDate,
  A_BusinessPartner_0.LastChangeDate
FROM API_BUSINESS_PARTNER_A_BusinessPartner AS A_BusinessPartner_0;

CREATE VIEW BusinessPartnerService_S4Addresses AS SELECT
  A_BusinessPartnerAddress_0.BusinessPartner,
  A_BusinessPartnerAddress_0.AddressID,
  A_BusinessPartnerAddress_0.Country,
  A_BusinessPartnerAddress_0.Region,
  A_BusinessPartnerAddress_0.PostalCode,
  A_BusinessPartnerAddress_0.CityName,
  A_BusinessPartnerAddress_0.StreetName,
  A_BusinessPartnerAddress_0.HouseNumber,
  A_BusinessPartnerAddress_0.IsDefaultAddress
FROM API_BUSINESS_PARTNER_A_BusinessPartnerAddress AS A_BusinessPartnerAddress_0;

CREATE VIEW BusinessPartnerService_S4EmailAddresses AS SELECT
  A_AddressEmailAddress_0.AddressID,
  A_AddressEmailAddress_0.Person,
  A_AddressEmailAddress_0.OrdinalNumber,
  A_AddressEmailAddress_0.IsDefaultEmailAddress,
  A_AddressEmailAddress_0.EmailAddress,
  A_AddressEmailAddress_0.SearchEmailAddress,
  A_AddressEmailAddress_0.AddressCommunicationRemarkText
FROM API_BUSINESS_PARTNER_A_AddressEmailAddress AS A_AddressEmailAddress_0;

CREATE VIEW BusinessPartnerService_S4PhoneNumbers AS SELECT
  A_AddressPhoneNumber_0.AddressID,
  A_AddressPhoneNumber_0.Person,
  A_AddressPhoneNumber_0.OrdinalNumber,
  A_AddressPhoneNumber_0.DestinationLocationCountry,
  A_AddressPhoneNumber_0.Connection,
  A_AddressPhoneNumber_0.IsDefaultPhoneNumber,
  A_AddressPhoneNumber_0.IsDefaultSmSNumber,
  A_AddressPhoneNumber_0.PhoneNumber,
  A_AddressPhoneNumber_0.PhoneNumberType,
  A_AddressPhoneNumber_0.PhoneExtensionNumber,
  A_AddressPhoneNumber_0.InternationalPhoneNumber,
  A_AddressPhoneNumber_0.AddressCommunicationRemarkText
FROM API_BUSINESS_PARTNER_A_AddressPhoneNumber AS A_AddressPhoneNumber_0;

CREATE VIEW SalesDocumentService_SalesDocHeaders AS SELECT
  SalesDocHeader_0.createdAt,
  SalesDocHeader_0.createdBy,
  SalesDocHeader_0.modifiedAt,
  SalesDocHeader_0.modifiedBy,
  SalesDocHeader_0.SalesDocument,
  SalesDocHeader_0.SalesOrganization,
  SalesDocHeader_0.DistributionChannel,
  SalesDocHeader_0.Division,
  SalesDocHeader_0.SalesDocumentDate,
  SalesDocHeader_0.SalesDocumentType,
  SalesDocHeader_0.CustomerID,
  SalesDocHeader_0.CustomerName,
  SalesDocHeader_0.CustomerGroup,
  SalesDocHeader_0.Currency,
  SalesDocHeader_0.TotalNetAmount,
  SalesDocHeader_0.Status,
  SalesDocHeader_0.ErrorMessage
FROM com_example_bp_SalesDocHeader AS SalesDocHeader_0;

CREATE VIEW SalesDocumentService_SalesDocItems AS SELECT
  SalesDocItem_0.createdAt,
  SalesDocItem_0.createdBy,
  SalesDocItem_0.modifiedAt,
  SalesDocItem_0.modifiedBy,
  SalesDocItem_0.SalesDocument,
  SalesDocItem_0.SalesDocumentItem,
  SalesDocItem_0.MaterialCode,
  SalesDocItem_0.MaterialName,
  SalesDocItem_0.MaterialGroup,
  SalesDocItem_0.OrderQuantity,
  SalesDocItem_0.OrderQuantityUnit,
  SalesDocItem_0.NetAmount,
  SalesDocItem_0.Currency,
  SalesDocItem_0.Plant,
  SalesDocItem_0.PlantName,
  SalesDocItem_0.CompanyCode,
  SalesDocItem_0.StorageLocation,
  SalesDocItem_0.PricingDate
FROM com_example_bp_SalesDocItem AS SalesDocItem_0;

CREATE VIEW SalesDocumentService_SalesDocDetails AS SELECT
  SalesDocDetail_0.createdAt,
  SalesDocDetail_0.createdBy,
  SalesDocDetail_0.modifiedAt,
  SalesDocDetail_0.modifiedBy,
  SalesDocDetail_0.SalesDocument,
  SalesDocDetail_0.SalesDocumentItem,
  SalesDocDetail_0.SequentialNumber,
  SalesDocDetail_0.DetailCategory,
  SalesDocDetail_0.DetailText,
  SalesDocDetail_0.DetailAmount,
  SalesDocDetail_0.ConditionType,
  SalesDocDetail_0.ScheduleLineDate,
  SalesDocDetail_0.DeliveryScheduleQty,
  SalesDocDetail_0.Currency
FROM com_example_bp_SalesDocDetail AS SalesDocDetail_0;

CREATE VIEW SalesDocumentService_SalesDocItemView AS SELECT
  oh_0.SalesDocument,
  oh_0.SalesDocumentItem,
  oh_0.SalesOrganization,
  oh_0.DistributionChannel,
  oh_0.Division,
  oh_0.CustomerID,
  oh_0.MaterialCode,
  oh_0.Plant,
  cm_1.CustomerName,
  cm_1.CustomerGroup,
  cm_1.Currency AS CustomerCurrency,
  cm_1.PaymentTerms,
  cm_1.SalesDistrict,
  mm_2.MaterialName,
  mm_2.MaterialGroup,
  mm_2.BaseUnit,
  mm_2.ProductHierarchy,
  mm_2.TaxClassification,
  pm_3.PlantName,
  pm_3.CompanyCode,
  pm_3.FactoryCalendar
FROM (((com_example_bp_OrderHeader AS oh_0 LEFT JOIN com_example_bp_master_CustomerMaster AS cm_1 ON cm_1.CustomerID = oh_0.CustomerID AND cm_1.SalesOrganization = oh_0.SalesOrganization AND cm_1.DistributionChannel = oh_0.DistributionChannel AND cm_1.Division = oh_0.Division) LEFT JOIN com_example_bp_master_MaterialMaster AS mm_2 ON mm_2.MaterialCode = oh_0.MaterialCode) LEFT JOIN com_example_bp_master_PlantMaster AS pm_3 ON pm_3.Plant = oh_0.Plant);

CREATE VIEW SalesDocumentService_S4SalesDocuments AS SELECT
  ZcSalesDocument_0.SalesDocument,
  ZcSalesDocument_0.SalesDocumentItem,
  ZcSalesDocument_0.SequentialNumber,
  ZcSalesDocument_0.SalesOrganization,
  ZcSalesDocument_0.DistributionChannel,
  ZcSalesDocument_0.Division,
  ZcSalesDocument_0.SalesDocumentDate,
  ZcSalesDocument_0.SalesDocumentType,
  ZcSalesDocument_0.CustomerID,
  ZcSalesDocument_0.MaterialCode,
  ZcSalesDocument_0.OrderQuantity,
  ZcSalesDocument_0.OrderQuantityUnit,
  ZcSalesDocument_0.NetAmount,
  ZcSalesDocument_0.Currency,
  ZcSalesDocument_0.Plant,
  ZcSalesDocument_0.StorageLocation,
  ZcSalesDocument_0.PricingDate,
  ZcSalesDocument_0.DetailCategory,
  ZcSalesDocument_0.DetailText,
  ZcSalesDocument_0.DetailAmount,
  ZcSalesDocument_0.ConditionType,
  ZcSalesDocument_0.ScheduleLineDate,
  ZcSalesDocument_0.DeliveryScheduleQty
FROM ZC_SALESDOCUMENT_SERVICE_ZcSalesDocument AS ZcSalesDocument_0;
