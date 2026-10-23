SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.QS_V_STAFF','V') IS NOT NULL DROP VIEW dbo.QS_V_STAFF;
IF OBJECT_ID('dbo.QS_LOG','U') IS NOT NULL DROP TABLE dbo.QS_LOG;
IF OBJECT_ID('dbo.QS_TRADE','U') IS NOT NULL DROP TABLE dbo.QS_TRADE;
IF OBJECT_ID('dbo.QS_ORDER','U') IS NOT NULL DROP TABLE dbo.QS_ORDER;
IF OBJECT_ID('dbo.QS_PRODUCT','U') IS NOT NULL DROP TABLE dbo.QS_PRODUCT;
IF OBJECT_ID('dbo.QS_STAFF','U') IS NOT NULL DROP TABLE dbo.QS_STAFF;
CREATE TABLE dbo.QS_STAFF (
  STAFF_ID varchar(32) NOT NULL, STAFF_CODE varchar(20) NOT NULL, STAFF_NAME nvarchar(60) NOT NULL,
  SEX int DEFAULT 1, BIRTHDAY date, ENTRY_TIME datetime2(6), SALARY decimal(10,2),
  STATUS char(1) DEFAULT 'A', BIG_REMARK nvarchar(max), PHOTO varbinary(max),
  EMBEDDING VECTOR(3), BONUS decimal(10,2) DEFAULT 0,
  TOTAL_PAY AS (SALARY + BONUS) PERSISTED,
  CREATE_TIME datetime2 NOT NULL DEFAULT SYSDATETIME(),
  LAST_MODIFIED datetime2 NOT NULL DEFAULT SYSDATETIME(),
  CONSTRAINT PK_QS_STAFF PRIMARY KEY (STAFF_ID), CONSTRAINT UK_QS_CODE UNIQUE (STAFF_CODE)
);
CREATE INDEX IDX_QS_NAME ON dbo.QS_STAFF (STAFF_NAME);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工信息表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工工号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_CODE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工姓名',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_NAME';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'性别:1男2女',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'SEX';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'生日',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'BIRTHDAY';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'入职时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'ENTRY_TIME';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'薪资',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'SALARY';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'状态:A在职B离职',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'STATUS';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'大文本备注(CLOB场景)',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'BIG_REMARK';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'照片二进制(BLOB场景)',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'PHOTO';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'向量嵌入',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'EMBEDDING';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'奖金基数',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'BONUS';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'总收入(计算列PERSISTED)',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'TOTAL_PAY';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'创建时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'CREATE_TIME';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'最后修改时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_STAFF',@level2type=N'COLUMN',@level2name=N'LAST_MODIFIED';
CREATE TABLE dbo.QS_ORDER (
  ORDER_ID varchar(32) NOT NULL, ORDER_LINE int NOT NULL, STAFF_ID varchar(32) NOT NULL,
  AMOUNT decimal(12,4) NOT NULL DEFAULT 0, CREATE_TIME datetime2 DEFAULT SYSDATETIME(),
  CONSTRAINT PK_QS_ORDER PRIMARY KEY (ORDER_ID, ORDER_LINE),
  CONSTRAINT FK_QS_ORD FOREIGN KEY (STAFF_ID) REFERENCES dbo.QS_STAFF (STAFF_ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单明细表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER',@level2type=N'COLUMN',@level2name=N'ORDER_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单行号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER',@level2type=N'COLUMN',@level2name=N'ORDER_LINE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'销售员工ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER',@level2type=N'COLUMN',@level2name=N'STAFF_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'数量',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER',@level2type=N'COLUMN',@level2name=N'AMOUNT';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'创建时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_ORDER',@level2type=N'COLUMN',@level2name=N'CREATE_TIME';
CREATE TABLE dbo.QS_PRODUCT (
  ID bigint IDENTITY(1,1) NOT NULL, PRODUCT_NAME nvarchar(100) NOT NULL,
  PRODUCT_TYPE int DEFAULT 1, WEIGHT float, CREATED_AT datetime2(3) DEFAULT SYSDATETIME(),
  CONSTRAINT PK_QS_PROD PRIMARY KEY (ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'产品表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'产品ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT',@level2type=N'COLUMN',@level2name=N'ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'产品名称',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT',@level2type=N'COLUMN',@level2name=N'PRODUCT_NAME';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'产品类型',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT',@level2type=N'COLUMN',@level2name=N'PRODUCT_TYPE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'重量',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT',@level2type=N'COLUMN',@level2name=N'WEIGHT';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'创建时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_PRODUCT',@level2type=N'COLUMN',@level2name=N'CREATED_AT';
-- 分区表:分区函数+分区方案,PK须包含分区键(对齐分区);
-- quickvo经sys.partition_schemes/functions/index_columns.partition_ordinal提取
IF EXISTS (SELECT * FROM sys.partition_schemes WHERE name='ps_trade') DROP PARTITION SCHEME ps_trade;
IF EXISTS (SELECT * FROM sys.partition_functions WHERE name='pf_trade') DROP PARTITION FUNCTION pf_trade;
CREATE PARTITION FUNCTION pf_trade (date) AS RANGE RIGHT FOR VALUES ('2026-02-01', '2026-03-01');
CREATE PARTITION SCHEME ps_trade AS PARTITION pf_trade ALL TO ([PRIMARY]);
CREATE TABLE dbo.QS_TRADE (
  TRADE_ID varchar(32) NOT NULL, TRADE_DATE date NOT NULL, AMOUNT decimal(12,2), STATUS int DEFAULT 0,
  CONSTRAINT PK_QS_TRADE PRIMARY KEY CLUSTERED (TRADE_ID, TRADE_DATE) ON ps_trade (TRADE_DATE)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'交易流水表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_TRADE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'交易ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_TRADE',@level2type=N'COLUMN',@level2name=N'TRADE_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'交易日期',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_TRADE',@level2type=N'COLUMN',@level2name=N'TRADE_DATE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'交易金额',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_TRADE',@level2type=N'COLUMN',@level2name=N'AMOUNT';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'交易状态',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_TRADE',@level2type=N'COLUMN',@level2name=N'STATUS';
CREATE TABLE dbo.QS_LOG (
  LOG_ID varchar(32) NOT NULL, STAFF_ID varchar(32) NOT NULL REFERENCES dbo.QS_STAFF (STAFF_ID),
  ACTION nvarchar(50) NOT NULL, ACTION_TIME datetime2 NOT NULL,
  CONSTRAINT PK_QS_LOG PRIMARY KEY (LOG_ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'操作日志表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_LOG';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'日志ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_LOG',@level2type=N'COLUMN',@level2name=N'LOG_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_LOG',@level2type=N'COLUMN',@level2name=N'STAFF_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'操作',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_LOG',@level2type=N'COLUMN',@level2name=N'ACTION';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'操作时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QS_LOG',@level2type=N'COLUMN',@level2name=N'ACTION_TIME';
