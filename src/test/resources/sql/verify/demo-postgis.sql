-- postgis几何类型验证(postgis容器,5434)
CREATE SCHEMA IF NOT EXISTS quickvo_demo;
DROP TABLE IF EXISTS quickvo_demo.QUICKVO_DEMO_GEO;
CREATE TABLE quickvo_demo.QUICKVO_DEMO_GEO (
  GEO_ID varchar(32) NOT NULL,
  CITY varchar(60),
  LOCATION geometry(Point, 4326),
  BOUNDARY geometry(Polygon, 4326),
  PATH_LINE geometry(LineString, 4326),
  CONSTRAINT PK_GEO PRIMARY KEY (GEO_ID)
);
COMMENT ON TABLE quickvo_demo.QUICKVO_DEMO_GEO IS '地理信息表';
COMMENT ON COLUMN quickvo_demo.QUICKVO_DEMO_GEO.LOCATION IS '经纬度点';
COMMENT ON COLUMN quickvo_demo.QUICKVO_DEMO_GEO.BOUNDARY IS '行政边界多边形';
