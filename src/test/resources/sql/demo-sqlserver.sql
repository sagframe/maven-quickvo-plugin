-- quickvo sqlserver方言验证用表(master库,dbo schema)
IF OBJECT_ID('dbo.QUICKVO_DEMO_V_STAFF','V') IS NOT NULL DROP VIEW dbo.QUICKVO_DEMO_V_STAFF;
IF OBJECT_ID('dbo.QUICKVO_DEMO_STAFF_LOG','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_STAFF_LOG;
IF OBJECT_ID('dbo.QUICKVO_DEMO_ORDER','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_ORDER;
IF OBJECT_ID('dbo.QUICKVO_DEMO_STAFF','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_STAFF;
GO

CREATE TABLE dbo.QUICKVO_DEMO_STAFF (
  STAFF_ID varchar(32) NOT NULL,
  STAFF_CODE varchar(20) NOT NULL,
  STAFF_NAME nvarchar(60) NOT NULL,
  SEX tinyint DEFAULT 1,
  BIRTHDAY date,
  ENTRY_DATE datetime2(6),
  SALARY decimal(10,2),
  STATUS char(1) DEFAULT '1',
  REMARK nvarchar(max),
  CONSTRAINT PK_QUICKVO_STAFF PRIMARY KEY (STAFF_ID),
  CONSTRAINT UK_QUICKVO_STAFF_CODE UNIQUE (STAFF_CODE)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工信息表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工工号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_CODE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工姓名',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'STAFF_NAME';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'性别:1男;2女',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'SEX';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'生日',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'BIRTHDAY';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'入职时间',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'ENTRY_DATE';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'薪资',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'SALARY';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'状态:1在职;2离职',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'STATUS';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'备注',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF',@level2type=N'COLUMN',@level2name=N'REMARK';

CREATE TABLE dbo.QUICKVO_DEMO_ORDER (
  ORDER_ID varchar(32) NOT NULL,
  ORDER_LINE int NOT NULL,
  STAFF_ID varchar(32) NOT NULL,
  AMOUNT decimal(12,4) NOT NULL DEFAULT 0,
  PRICE decimal(12,4) NOT NULL DEFAULT 0,
  TOTAL_AMT decimal(18,2),
  CREATE_TIME datetime2(6) NOT NULL DEFAULT SYSDATETIME(),
  CONSTRAINT PK_QUICKVO_ORDER PRIMARY KEY (ORDER_ID, ORDER_LINE),
  CONSTRAINT FK_QUICKVO_ORDER_STAFF FOREIGN KEY (STAFF_ID) REFERENCES dbo.QUICKVO_DEMO_STAFF (STAFF_ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单明细表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_ORDER';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_ORDER',@level2type=N'COLUMN',@level2name=N'ORDER_ID';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'订单行号',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_ORDER',@level2type=N'COLUMN',@level2name=N'ORDER_LINE';

-- 自增主键(identity策略)
CREATE TABLE dbo.QUICKVO_DEMO_STAFF_LOG (
  LOG_ID bigint IDENTITY(1,1) NOT NULL,
  STAFF_ID varchar(32) NOT NULL,
  OPERATE nvarchar(50) NOT NULL,
  OPERATE_TIME datetime2(6) NOT NULL,
  CONSTRAINT PK_QUICKVO_STAFF_LOG PRIMARY KEY (LOG_ID),
  CONSTRAINT FK_QUICKVO_LOG_STAFF FOREIGN KEY (STAFF_ID) REFERENCES dbo.QUICKVO_DEMO_STAFF (STAFF_ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'员工操作日志表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF_LOG';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'日志ID',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_STAFF_LOG',@level2type=N'COLUMN',@level2name=N'LOG_ID';
GO

CREATE VIEW dbo.QUICKVO_DEMO_V_STAFF AS
SELECT STAFF_ID, STAFF_CODE, STAFF_NAME, SEX, STATUS FROM dbo.QUICKVO_DEMO_STAFF;
GO
