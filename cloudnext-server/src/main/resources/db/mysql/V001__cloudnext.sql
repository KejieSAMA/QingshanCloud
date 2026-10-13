-- V001: CloudNext MySQL 8.0 baseline. Apply explicitly to an EMPTY dedicated database.
-- No CREATE DATABASE, USE, DROP, IF NOT EXISTS or cross-schema statements.

CREATE TABLE `cloudnext_audit_log` (
  `id` VARCHAR(64) NOT NULL,
  `person_oa_id` VARCHAR(64) NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `target_type` VARCHAR(32) NOT NULL,
  `target_id` VARCHAR(256) NOT NULL,
  `payload` LONGTEXT,
  `ip_address` VARCHAR(64) NOT NULL,
  `user_agent` VARCHAR(512) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_person_created` (`person_oa_id`, `created_at`),
  KEY `idx_audit_action_created` (`action`, `created_at`),
  KEY `idx_audit_target` (`target_type`, `target_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_auth_session` (
  `id` VARCHAR(64) NOT NULL,
  `token` VARCHAR(128) NOT NULL,
  `person_oa_id` VARCHAR(64) NOT NULL,
  `auth_provider` VARCHAR(32),
  `tenant_id` VARCHAR(128),
  `form_employee_id` VARCHAR(128),
  `employee_no` VARCHAR(128),
  `auth_version` BIGINT,
  `person_name` VARCHAR(255) NOT NULL,
  `person_distinguished_name` VARCHAR(255) NOT NULL,
  `token_type` VARCHAR(32) NOT NULL,
  `role_list_json` VARCHAR(4000) NOT NULL,
  `identity_list_json` VARCHAR(4000) NOT NULL,
  `expires_at` VARCHAR(64) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_token` (`token`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_canonical_alias` (
  `id` VARCHAR(64) NOT NULL,
  `subject_type` VARCHAR(32) NOT NULL,
  `alias_key` VARCHAR(128) NOT NULL,
  `canonical_key` VARCHAR(128) NOT NULL,
  `relation_type` VARCHAR(32) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_canonical_alias` (`subject_type`, `alias_key`, `canonical_key`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_directory_sync_state` (
  `source_key` VARCHAR(64) NOT NULL,
  `etag` VARCHAR(128),
  `last_outcome` VARCHAR(32) NOT NULL,
  `last_started_at` VARCHAR(64),
  `last_success_at` VARCHAR(64),
  `attempt_generation` BIGINT,
  `last_success_generation` BIGINT,
  `last_success_cutover_version` VARCHAR(128),
  `last_failure_at` VARCHAR(64),
  `failure_message` LONGTEXT,
  `snapshot_tenant_id` VARCHAR(128),
  `foundation_sot` VARCHAR(32),
  `foundation_write_impl` VARCHAR(32),
  `person_count` BIGINT,
  `unit_count` BIGINT,
  `identity_count` BIGINT,
  `updated_at` VARCHAR(64) NOT NULL,
  `entity_version` BIGINT NOT NULL,
  PRIMARY KEY (`source_key`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_item` (
  `id` VARCHAR(64) NOT NULL,
  `parent_id` VARCHAR(64) NOT NULL,
  `owner_person_oa_id` VARCHAR(64) NOT NULL,
  `owner_name` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `item_type` VARCHAR(16) NOT NULL,
  `file_type` VARCHAR(32) NOT NULL,
  `content_type` VARCHAR(255) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `storage_key` VARCHAR(255) NOT NULL,
  `scope_type` VARCHAR(16) NOT NULL DEFAULT 'PERSONAL',
  `scope_id` VARCHAR(64) NOT NULL DEFAULT '',
  `storage_backend` VARCHAR(16) NOT NULL DEFAULT 'local',
  `deleted` BIT(1) NOT NULL,
  `deleted_at` VARCHAR(64) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  `last_accessed_at` VARCHAR(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_drive_item_owner_deleted_parent_type_updated` (`owner_person_oa_id`, `deleted`, `parent_id`, `item_type`, `updated_at`),
  KEY `idx_drive_item_owner_deleted_parent_name` (`owner_person_oa_id`, `deleted`, `parent_id`, `name`),
  KEY `idx_drive_item_owner_deleted_deleted_at` (`owner_person_oa_id`, `deleted`, `deleted_at`),
  KEY `idx_drive_item_owner_deleted_last_accessed` (`owner_person_oa_id`, `deleted`, `last_accessed_at`),
  KEY `idx_drive_item_scope_deleted_parent_updated` (`scope_type`, `scope_id`, `deleted`, `parent_id`, `updated_at`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_item_version` (
  `id` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(64) NOT NULL,
  `version_number` BIGINT NOT NULL,
  `storage_key` VARCHAR(255) NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(255) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `person_key` VARCHAR(64) NOT NULL,
  `person_name` VARCHAR(255) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drive_item_version_1` (`item_id`, `version_number`),
  KEY `idx_version_item` (`item_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_public_link` (
  `record_hidden` BIT(1) NOT NULL DEFAULT b'0',
  `id` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(64) NOT NULL,
  `creator_person_oa_id` VARCHAR(64) NOT NULL,
  `creator_name` VARCHAR(255) NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `permission` VARCHAR(16) NOT NULL,
  `password_hash` VARCHAR(128) NOT NULL,
  `expires_at` VARCHAR(64) NOT NULL,
  `download_limit` BIGINT NOT NULL,
  `download_count` BIGINT NOT NULL,
  `revoked` BIT(1) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drive_public_link_token` (`token`),
  KEY `idx_dpl_creator_revoked_created` (`creator_person_oa_id`, `revoked`, `created_at`),
  KEY `idx_dpl_item` (`item_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_public_link_access_log` (
  `id` VARCHAR(64) NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `action_type` VARCHAR(16) NOT NULL,
  `success` BIT(1) NOT NULL,
  `fail_reason` VARCHAR(32) NOT NULL,
  `ip_address` VARCHAR(64) NOT NULL,
  `user_agent` VARCHAR(512) NOT NULL,
  `attempted_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_link_access_token_attempted` (`token`, `attempted_at`),
  KEY `idx_link_access_attempted` (`attempted_at`),
  KEY `idx_link_access_token_success` (`token`, `success`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_share` (
  `id` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(64) NOT NULL,
  `owner_person_oa_id` VARCHAR(64) NOT NULL,
  `owner_name` VARCHAR(255) NOT NULL,
  `target_type` VARCHAR(16) NOT NULL,
  `target_id` VARCHAR(64) NOT NULL,
  `target_name` VARCHAR(255) NOT NULL,
  `permission` VARCHAR(16) NOT NULL,
  `expires_at` VARCHAR(64) NOT NULL,
  `revoked` BIT(1) NOT NULL,
  `can_reshare` BIT(1) NOT NULL DEFAULT b'0',
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drive_share_owner_item_target` (`owner_person_oa_id`, `item_id`, `target_type`, `target_id`),
  KEY `idx_drive_share_owner_revoked_updated` (`owner_person_oa_id`, `revoked`, `updated_at`),
  KEY `idx_drive_share_target_revoked_updated` (`target_type`, `target_id`, `revoked`, `updated_at`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_user_item` (
  `id` VARCHAR(64) NOT NULL,
  `person_key` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(64) NOT NULL,
  `favorite` BIT(1) NOT NULL,
  `opened_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_drive_user_item_1` (`person_key`, `item_id`),
  KEY `idx_user_item_person` (`person_key`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_drive_write_receipt` (
  `id` VARCHAR(64) NOT NULL,
  `item_id` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_receipt_item` (`item_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_identity_binding` (
  `id` VARCHAR(64) NOT NULL,
  `tenant_id` VARCHAR(128) NOT NULL,
  `form_assignment_id` VARCHAR(128) NOT NULL,
  `canonical_identity_id` VARCHAR(128) NOT NULL,
  `canonical_person_id` VARCHAR(128) NOT NULL,
  `canonical_unit_id` VARCHAR(128) NOT NULL,
  `legacy_oa_identity_id` VARCHAR(128),
  `position_id` VARCHAR(128),
  `identity_name` VARCHAR(255),
  `display_path` VARCHAR(1024),
  `primary_assignment` BIT(1) NOT NULL,
  `record_status` VARCHAR(32) NOT NULL,
  `directory_status` VARCHAR(32) NOT NULL,
  `migration_decision` VARCHAR(32) NOT NULL,
  `decision_note` VARCHAR(2000),
  `decision_by` VARCHAR(128),
  `decided_at` VARCHAR(64),
  `last_seen_etag` VARCHAR(128),
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  `entity_version` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_form_subject` (`tenant_id`, `form_assignment_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_legacy_archive_task` (
  `version` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `checksum` VARCHAR(64) NOT NULL,
  `snapshot_json` LONGTEXT NOT NULL,
  `inventory_json` LONGTEXT,
  `form_snapshot_etag` VARCHAR(128),
  `form_snapshot_generation` BIGINT,
  `form_snapshot_success_at` VARCHAR(64),
  `unit_count` BIGINT,
  `person_count` BIGINT,
  `identity_count` BIGINT,
  `role_count` BIGINT,
  `group_count` BIGINT,
  `membership_count` BIGINT,
  `created_by` VARCHAR(128),
  `approved_by` VARCHAR(128),
  `applied_by` VARCHAR(128),
  `verified_by` VARCHAR(128),
  `created_at` VARCHAR(64),
  `approved_at` VARCHAR(64),
  `applied_at` VARCHAR(64),
  `verified_at` VARCHAR(64),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_meta` (
  `meta_key` VARCHAR(64) NOT NULL,
  `meta_value` VARCHAR(255) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`meta_key`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_migration_decision_audit` (
  `id` VARCHAR(64) NOT NULL,
  `subject_type` VARCHAR(32) NOT NULL,
  `subject_id` VARCHAR(128) NOT NULL,
  `actor_person_key` VARCHAR(128) NOT NULL,
  `before_value` VARCHAR(2000),
  `after_value` VARCHAR(2000),
  `note` VARCHAR(2000),
  `created_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_group` (
  `oa_id` VARCHAR(64) NOT NULL,
  `unique_name` VARCHAR(255),
  `distinguished_name` VARCHAR(255),
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255),
  `status` VARCHAR(64),
  `updated_at` VARCHAR(64) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_group_oa_id` (`oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_identity` (
  `oa_id` VARCHAR(64) NOT NULL,
  `unique_name` VARCHAR(255),
  `distinguished_name` VARCHAR(255),
  `person_oa_id` VARCHAR(64),
  `unit_oa_id` VARCHAR(64),
  `unit_name` VARCHAR(255),
  `unit_level` INT,
  `unit_level_name` VARCHAR(1024),
  `major_flag` BIT(1),
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255),
  `status` VARCHAR(64),
  `updated_at` VARCHAR(64) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_identity_oa_id` (`oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_membership` (
  `id` VARCHAR(128) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_oa_id` VARCHAR(64) NOT NULL,
  `target_type` VARCHAR(32) NOT NULL,
  `target_oa_id` VARCHAR(64) NOT NULL,
  `relation_type` VARCHAR(32) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_person` (
  `oa_id` VARCHAR(64) NOT NULL,
  `unique_name` VARCHAR(255),
  `employee_no` VARCHAR(128),
  `distinguished_name` VARCHAR(255),
  `mobile` VARCHAR(128),
  `mail` VARCHAR(255),
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255),
  `status` VARCHAR(64),
  `updated_at` VARCHAR(64) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_person_oa_id` (`oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_role` (
  `oa_id` VARCHAR(64) NOT NULL,
  `unique_name` VARCHAR(255),
  `distinguished_name` VARCHAR(255),
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255),
  `status` VARCHAR(64),
  `updated_at` VARCHAR(64) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_role_oa_id` (`oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_org_unit` (
  `oa_id` VARCHAR(64) NOT NULL,
  `unique_name` VARCHAR(255),
  `distinguished_name` VARCHAR(255),
  `parent_oa_id` VARCHAR(64),
  `level_value` INT,
  `level_name` VARCHAR(1024),
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255),
  `status` VARCHAR(64),
  `updated_at` VARCHAR(64) NOT NULL,
  `sync_version` VARCHAR(64) NOT NULL,
  `synced_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_unit_oa_id` (`oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_principal_binding` (
  `id` VARCHAR(64) NOT NULL,
  `auth_provider` VARCHAR(32) NOT NULL,
  `tenant_id` VARCHAR(128) NOT NULL,
  `form_employee_id` VARCHAR(128) NOT NULL,
  `canonical_person_id` VARCHAR(128) NOT NULL,
  `legacy_oa_person_id` VARCHAR(128),
  `employee_no_snapshot` VARCHAR(128),
  `display_name` VARCHAR(255),
  `display_path` VARCHAR(1024),
  `account_enabled` BIT(1) NOT NULL,
  `source_account_enabled` BIT(1) NOT NULL,
  `auth_version` BIGINT NOT NULL,
  `record_status` VARCHAR(32) NOT NULL,
  `directory_status` VARCHAR(32) NOT NULL,
  `migration_decision` VARCHAR(32) NOT NULL,
  `decision_note` VARCHAR(2000),
  `decision_by` VARCHAR(128),
  `decided_at` VARCHAR(64),
  `role_list_json` LONGTEXT,
  `last_seen_etag` VARCHAR(128),
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  `entity_version` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_principal_form_subject` (`auth_provider`, `tenant_id`, `form_employee_id`),
  UNIQUE KEY `uk_principal_canonical` (`canonical_person_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_sso_bootstrap` (
  `token_hash` VARCHAR(64) NOT NULL,
  `session_token` VARCHAR(128) NOT NULL,
  `return_path` VARCHAR(1024) NOT NULL,
  `expires_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`token_hash`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_storage_cleanup` (
  `storage_key` VARCHAR(255) NOT NULL,
  `queued_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`storage_key`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_unit_binding` (
  `id` VARCHAR(64) NOT NULL,
  `tenant_id` VARCHAR(128) NOT NULL,
  `form_org_id` VARCHAR(128) NOT NULL,
  `canonical_unit_id` VARCHAR(128) NOT NULL,
  `legacy_oa_unit_id` VARCHAR(128),
  `parent_canonical_unit_id` VARCHAR(128),
  `unit_name` VARCHAR(255),
  `display_path` VARCHAR(1024),
  `record_status` VARCHAR(32) NOT NULL,
  `directory_status` VARCHAR(32) NOT NULL,
  `migration_decision` VARCHAR(32) NOT NULL,
  `decision_note` VARCHAR(2000),
  `decision_by` VARCHAR(128),
  `decided_at` VARCHAR(64),
  `canonical_aliases_json` LONGTEXT,
  `last_seen_etag` VARCHAR(128),
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  `entity_version` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unit_form_subject` (`tenant_id`, `form_org_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_user_preference` (
  `id` VARCHAR(64) NOT NULL,
  `person_oa_id` VARCHAR(64) NOT NULL,
  `preference_key` VARCHAR(64) NOT NULL,
  `preference_value` VARCHAR(255) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cloudnext_user_preference` (`person_oa_id`, `preference_key`),
  KEY `idx_cloudnext_user_preference_person` (`person_oa_id`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_workspace` (
  `id` VARCHAR(64) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `creator_person_oa_id` VARCHAR(64) NOT NULL,
  `creator_name` VARCHAR(255) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `root_item_id` VARCHAR(64) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workspace_status_updated` (`status`, `updated_at`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;

CREATE TABLE `cloudnext_workspace_member` (
  `id` VARCHAR(64) NOT NULL,
  `workspace_id` VARCHAR(64) NOT NULL,
  `person_oa_id` VARCHAR(64) NOT NULL,
  `person_name` VARCHAR(255) NOT NULL,
  `role` VARCHAR(16) NOT NULL,
  `created_at` VARCHAR(64) NOT NULL,
  `updated_at` VARCHAR(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ws_member` (`workspace_id`, `person_oa_id`),
  KEY `idx_ws_member_person` (`person_oa_id`, `workspace_id`),
  KEY `idx_ws_member_workspace_role` (`workspace_id`, `role`)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
