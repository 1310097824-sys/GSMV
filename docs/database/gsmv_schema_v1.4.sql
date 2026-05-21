
-- GSMV v1.4 current database schema
-- Generated from local MySQL database `gsmv`.
-- Usage: copy this whole file into MySQL 8.0+ and execute it.
-- Scope: schema only. It creates database/tables/indexes/foreign keys, but does not insert sample data.
-- If you want demo data, prefer starting the application with an empty database and let Flyway run migrations.

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `gsmv` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `gsmv`;
DROP TABLE IF EXISTS `ai_research_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_research_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_type` varchar(24) NOT NULL DEFAULT 'MONTHLY',
  `days` int NOT NULL DEFAULT '30',
  `title` varchar(255) NOT NULL,
  `summary` text NOT NULL,
  `highlights_json` json NOT NULL,
  `risks_json` json NOT NULL,
  `recommendations_json` json NOT NULL,
  `evidence_json` json NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ai_report_created` (`created_at`),
  KEY `idx_ai_report_type` (`report_type`),
  KEY `fk_ai_report_user` (`created_by`),
  CONSTRAINT `fk_ai_report_user` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ai_review_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_review_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(32) NOT NULL DEFAULT 'SPECIES_IDENTIFY',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `resolution_code` varchar(24) DEFAULT NULL,
  `submitted_by` bigint NOT NULL,
  `reviewer_user_id` bigint DEFAULT NULL,
  `image_media_id` bigint DEFAULT NULL,
  `likely_chinese_name` varchar(128) DEFAULT NULL,
  `likely_scientific_name` varchar(128) DEFAULT NULL,
  `confidence` decimal(5,4) NOT NULL DEFAULT '0.0000',
  `needs_human_review` tinyint NOT NULL DEFAULT '1',
  `reasoning` text,
  `candidate_json` json DEFAULT NULL,
  `related_species_json` json DEFAULT NULL,
  `initial_recognition_json` json DEFAULT NULL,
  `rag_evidence_json` json DEFAULT NULL,
  `review_evidence_json` json DEFAULT NULL,
  `submit_note` text,
  `final_species_id` bigint DEFAULT NULL,
  `final_chinese_name` varchar(128) DEFAULT NULL,
  `final_scientific_name` varchar(128) DEFAULT NULL,
  `review_note` text,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ai_review_status` (`status`),
  KEY `idx_ai_review_submitter` (`submitted_by`),
  KEY `idx_ai_review_reviewer` (`reviewer_user_id`),
  KEY `idx_ai_review_created` (`created_at`),
  KEY `fk_ai_review_image` (`image_media_id`),
  KEY `fk_ai_review_species` (`final_species_id`),
  CONSTRAINT `fk_ai_review_image` FOREIGN KEY (`image_media_id`) REFERENCES `media_file` (`id`),
  CONSTRAINT `fk_ai_review_reviewer` FOREIGN KEY (`reviewer_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_ai_review_species` FOREIGN KEY (`final_species_id`) REFERENCES `species` (`id`),
  CONSTRAINT `fk_ai_review_submitter` FOREIGN KEY (`submitted_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `module` varchar(32) NOT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(32) DEFAULT NULL,
  `entity_id` bigint DEFAULT NULL,
  `request_id` varchar(64) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `success` tinyint NOT NULL DEFAULT '1',
  `detail_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_time` (`created_at`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_module_entity` (`module`,`entity_type`,`entity_id`),
  CONSTRAINT `fk_audit_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=397 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecosystem`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ecosystem` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `type` varchar(32) DEFAULT NULL,
  `description` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ecosystem_type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `entity_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `entity_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(32) NOT NULL,
  `entity_id` bigint NOT NULL,
  `version_no` int NOT NULL,
  `action` varchar(32) NOT NULL,
  `snapshot_json` longtext NOT NULL,
  `diff_json` longtext NOT NULL,
  `changed_by` bigint NOT NULL,
  `rollback_source_version_id` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_entity_version` (`entity_type`,`entity_id`,`version_no`),
  KEY `fk_entity_version_user` (`changed_by`),
  KEY `fk_entity_version_source` (`rollback_source_version_id`),
  KEY `idx_entity_version_lookup` (`entity_type`,`entity_id`,`version_no` DESC),
  CONSTRAINT `fk_entity_version_source` FOREIGN KEY (`rollback_source_version_id`) REFERENCES `entity_version` (`id`),
  CONSTRAINT `fk_entity_version_user` FOREIGN KEY (`changed_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `media_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `media_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_type` varchar(32) NOT NULL,
  `business_id` bigint NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `stored_filename` varchar(255) NOT NULL,
  `content_type` varchar(128) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `storage_path` varchar(512) NOT NULL,
  `sha256` char(64) DEFAULT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `uploaded_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_media_business` (`business_type`,`business_id`),
  KEY `idx_media_uploaded_by` (`uploaded_by`),
  CONSTRAINT `fk_media_uploaded_by` FOREIGN KEY (`uploaded_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `observation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `observation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ecosystem_id` bigint NOT NULL,
  `observer_user_id` bigint NOT NULL,
  `observed_at` datetime(3) NOT NULL,
  `location_lat` decimal(10,7) NOT NULL,
  `location_lng` decimal(10,7) NOT NULL,
  `location_point` point NOT NULL /*!80003 SRID 4326 */,
  `location_name` varchar(128) DEFAULT NULL,
  `env_json` json DEFAULT NULL,
  `note` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_obs_time` (`observed_at`),
  KEY `idx_obs_ecosystem` (`ecosystem_id`),
  KEY `idx_obs_observer` (`observer_user_id`),
  SPATIAL KEY `sp_idx_obs_point` (`location_point`),
  CONSTRAINT `fk_obs_ecosystem` FOREIGN KEY (`ecosystem_id`) REFERENCES `ecosystem` (`id`),
  CONSTRAINT `fk_obs_observer` FOREIGN KEY (`observer_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `observation_species`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `observation_species` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `observation_id` bigint NOT NULL,
  `species_id` bigint NOT NULL,
  `count_estimated` int DEFAULT NULL,
  `behavior` varchar(255) DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_obs_species` (`observation_id`,`species_id`),
  KEY `idx_obs_species_species` (`species_id`),
  CONSTRAINT `fk_obs_species_obs` FOREIGN KEY (`observation_id`) REFERENCES `observation` (`id`),
  CONSTRAINT `fk_obs_species_species` FOREIGN KEY (`species_id`) REFERENCES `species` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `chunk_index` int NOT NULL,
  `title` varchar(255) NOT NULL,
  `summary` varchar(512) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `embedding_json` mediumtext,
  `vector_point_id` varchar(128) DEFAULT NULL,
  `embedding_model` varchar(64) DEFAULT NULL,
  `embedding_dimension` int DEFAULT NULL,
  `embedding_status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `embedding_error` varchar(1000) DEFAULT NULL,
  `character_count` int NOT NULL DEFAULT '0',
  `metadata_json` json DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'READY',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_chunk_document_index` (`document_id`,`chunk_index`),
  KEY `idx_rag_chunk_source` (`source_type`,`source_id`),
  KEY `idx_rag_chunk_status` (`status`),
  CONSTRAINT `fk_rag_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `rag_document` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=197 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(32) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `media_id` bigint DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `original_filename` varchar(255) DEFAULT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `chunk_count` int NOT NULL DEFAULT '0',
  `error_message` varchar(1000) DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_document_source` (`source_type`,`source_id`),
  KEY `idx_rag_document_status` (`status`),
  KEY `idx_rag_document_media` (`media_id`),
  KEY `idx_rag_document_uploaded_by` (`uploaded_by`),
  CONSTRAINT `fk_rag_document_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`),
  CONSTRAINT `fk_rag_document_uploaded_by` FOREIGN KEY (`uploaded_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_index_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_index_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_type` varchar(32) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'RUNNING',
  `target_source_type` varchar(32) DEFAULT NULL,
  `target_source_id` bigint DEFAULT NULL,
  `total_documents` int NOT NULL DEFAULT '0',
  `total_chunks` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `error_message` varchar(1000) DEFAULT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_rag_job_status` (`status`),
  KEY `idx_rag_job_created_at` (`created_at`),
  KEY `fk_rag_job_created_by` (`created_by`),
  CONSTRAINT `fk_rag_job_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_ingest_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_ingest_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL,
  `source_type` varchar(48) NOT NULL,
  `source_code` varchar(48) DEFAULT NULL,
  `external_id` varchar(255) DEFAULT NULL,
  `source_url` varchar(1000) DEFAULT NULL,
  `local_path` varchar(1000) DEFAULT NULL,
  `media_id` bigint DEFAULT NULL,
  `rag_document_id` bigint DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'PENDING',
  `error_message` varchar(1000) DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_rag_ingest_item_job` (`job_id`),
  KEY `idx_rag_ingest_item_status` (`status`),
  KEY `idx_rag_ingest_item_source` (`source_code`,`external_id`),
  KEY `fk_rag_ingest_item_media` (`media_id`),
  KEY `fk_rag_ingest_item_document` (`rag_document_id`),
  CONSTRAINT `fk_rag_ingest_item_document` FOREIGN KEY (`rag_document_id`) REFERENCES `rag_document` (`id`),
  CONSTRAINT `fk_rag_ingest_item_job` FOREIGN KEY (`job_id`) REFERENCES `rag_ingest_job` (`id`),
  CONSTRAINT `fk_rag_ingest_item_media` FOREIGN KEY (`media_id`) REFERENCES `media_file` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_ingest_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_ingest_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_type` varchar(32) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'RUNNING',
  `source_code` varchar(48) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `total_items` int NOT NULL DEFAULT '0',
  `processed_items` int NOT NULL DEFAULT '0',
  `success_count` int NOT NULL DEFAULT '0',
  `failed_count` int NOT NULL DEFAULT '0',
  `error_message` varchar(1000) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_rag_ingest_job_status` (`status`),
  KEY `idx_rag_ingest_job_created` (`created_at`),
  KEY `idx_rag_ingest_job_source` (`source_code`),
  KEY `fk_rag_ingest_job_user` (`created_by`),
  CONSTRAINT `fk_rag_ingest_job_user` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rag_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rag_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(48) NOT NULL,
  `name` varchar(128) NOT NULL,
  `source_type` varchar(48) NOT NULL,
  `base_url` varchar(512) DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT '1',
  `config_json` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_source_code` (`code`),
  KEY `idx_rag_source_type` (`source_type`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `species`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `species` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `taxon_id` bigint NOT NULL,
  `protection_level` varchar(32) DEFAULT NULL,
  `iucn_status` varchar(16) DEFAULT NULL,
  `description` text,
  `morphology` text,
  `habit` text,
  `habitat` text,
  `distribution` text,
  `distribution_lat` decimal(10,7) DEFAULT NULL,
  `distribution_lng` decimal(10,7) DEFAULT NULL,
  `geo_range_text` text,
  `video_url` varchar(512) DEFAULT NULL,
  `reference_text` text,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_species_taxon` (`taxon_id`),
  KEY `idx_species_protection` (`protection_level`),
  KEY `idx_species_iucn` (`iucn_status`),
  CONSTRAINT `fk_species_taxon` FOREIGN KEY (`taxon_id`) REFERENCES `taxon` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(128) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(64) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`permission_id`),
  KEY `idx_role_perm_perm` (`permission_id`),
  CONSTRAINT `fk_role_perm_perm` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `fk_role_perm_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `display_name` varchar(64) DEFAULT NULL,
  `email` varchar(128) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `bio` text,
  `approval_status` varchar(16) NOT NULL DEFAULT 'APPROVED',
  `approval_remark` varchar(255) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `avatar_media_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_email` (`email`),
  KEY `idx_sys_user_approval_status` (`approval_status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `assigned_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_role_role` (`role_id`),
  CONSTRAINT `fk_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `taxon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `taxon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `rank` varchar(16) NOT NULL,
  `scientific_name` varchar(128) NOT NULL,
  `chinese_name` varchar(128) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_taxon_unique` (`parent_id`,`rank`,`scientific_name`),
  KEY `idx_taxon_parent` (`parent_id`),
  CONSTRAINT `fk_taxon_parent` FOREIGN KEY (`parent_id`) REFERENCES `taxon` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
