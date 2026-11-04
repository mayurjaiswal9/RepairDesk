-- V14: geocode cache (an address is looked up at most once) and a daily call counter.

CREATE TABLE geocode_cache (
    address_hash       varchar(64)  PRIMARY KEY,
    normalized_address varchar(500) NOT NULL,
    lat                double precision NOT NULL,
    lng                double precision NOT NULL,
    formatted_address  varchar(500),
    created_at         timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE maps_api_usage (
    usage_day  date        NOT NULL,
    api        varchar(20) NOT NULL,
    call_count integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (usage_day, api),
    CONSTRAINT maps_api_usage_api_check   CHECK (api IN ('GEOCODE', 'ROUTES')),
    CONSTRAINT maps_api_usage_count_check CHECK (call_count >= 0)
);
