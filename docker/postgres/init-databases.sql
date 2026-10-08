-- init-databases.sql
-- Runs on initial postgres container startup to provision isolated databases for each service.
CREATE DATABASE product_db;
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
CREATE DATABASE payment_db;
CREATE DATABASE keycloak_db;
