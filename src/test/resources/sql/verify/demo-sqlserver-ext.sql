-- sqlserver2025: vector类型 + 时序表 + 持久化计算列
SET QUOTED_IDENTIFIER ON;
IF OBJECT_ID('dbo.QUICKVO_DEMO_AUDIT','U') IS NOT NULL ALTER TABLE dbo.QUICKVO_DEMO_AUDIT SET (SYSTEM_VERSIONING = OFF);
IF OBJECT_ID('dbo.QUICKVO_DEMO_AUDIT','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_AUDIT;
IF OBJECT_ID('dbo.QUICKVO_DEMO_AUDIT_History','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_AUDIT_History;
IF OBJECT_ID('dbo.QUICKVO_DEMO_AI_DOC','U') IS NOT NULL DROP TABLE dbo.QUICKVO_DEMO_AI_DOC;
GO
CREATE TABLE dbo.QUICKVO_DEMO_AI_DOC (
  DOC_ID bigint IDENTITY(1,1) NOT NULL,
  DOC_TITLE nvarchar(200),
  EMBEDDING VECTOR(3),
  PRICE decimal(10,2),
  TAX_PRICE AS (PRICE * 1.13) PERSISTED,
  CONSTRAINT PK_QUICKVO_AI_DOC PRIMARY KEY (DOC_ID)
);
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'AI文档表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_AI_DOC';
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'向量',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_AI_DOC',@level2type=N'COLUMN',@level2name=N'EMBEDDING';
-- 时序表(temporal): ValidFrom/ValidTo为系统生成列
CREATE TABLE dbo.QUICKVO_DEMO_AUDIT (
  AUDIT_ID int IDENTITY(1,1) NOT NULL,
  BIZ_KEY varchar(50) NOT NULL,
  ValidFrom datetime2(2) GENERATED ALWAYS AS ROW START NOT NULL,
  ValidTo datetime2(2) GENERATED ALWAYS AS ROW END NOT NULL,
  PERIOD FOR SYSTEM_TIME (ValidFrom, ValidTo),
  CONSTRAINT PK_QUICKVO_AUDIT PRIMARY KEY CLUSTERED (AUDIT_ID)
) WITH (SYSTEM_VERSIONING = ON (HISTORY_TABLE = dbo.QUICKVO_DEMO_AUDIT_History));
EXEC sp_addextendedproperty @name=N'MS_Description',@value=N'时序审计表',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'QUICKVO_DEMO_AUDIT';
GO
