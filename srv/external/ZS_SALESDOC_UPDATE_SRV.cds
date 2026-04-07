/* checksum : 7d4e0c170ef522f49d9301d500cfc116 */
@cds.external : true
@m.IsDefaultEntityContainer : 'true'
service ZS_SALESDOC_UPDATE_SRV {
  @cds.external : true
  @cds.persistence.skip : true
  @sap.updatable : 'false'
  @sap.deletable : 'false'
  @sap.searchable : 'false'
  @sap.content.version : '1'
  entity UpdateSalesDocStatus {
    @sap.label : 'Primary Key (Dummy)'
    key p_key : String(1) not null;
    @sap.label : 'Client Key (Dummy)'
    key c_key : String(1) not null;
    @sap.label : 'Sales Document'
    SalesDocument : String(10) not null;
    @sap.label : 'Sales Document Item'
    SalesDocumentItem : String(6) not null;
    @sap.label : 'Processing Status'
    ProcessingStatus : String(2) not null;
    @sap.label : 'Processed Date'
    ProcessedDate : String(10) not null;
    @sap.label : 'Processed By'
    ProcessedBy : String(12) not null;
    @sap.label : 'Remark'
    Remark : String(255) not null;
    @sap.label : 'Internal Code'
    InternalCode : String(20) not null;
  };

  @cds.external : true
  @cds.persistence.skip : true
  @sap.updatable : 'false'
  @sap.deletable : 'false'
  @sap.searchable : 'false'
  @sap.content.version : '1'
  entity InsertSalesDocLog {
    @sap.label : 'Primary Key (Dummy)'
    key p_key : String(1) not null;
    @sap.label : 'Client Key (Dummy)'
    key c_key : String(1) not null;
    @sap.label : 'Sales Document'
    SalesDocument : String(10) not null;
    @sap.label : 'Sales Document Item'
    SalesDocumentItem : String(6) not null;
    @sap.label : 'Log Type (S=Success/E=Error)'
    LogType : String(1) not null;
    @sap.label : 'Log Message'
    LogMessage : String(255) not null;
  };
};

