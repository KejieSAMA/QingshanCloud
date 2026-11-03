-- EXPLICIT MAINTENANCE ONLY. This file is not executed by application startup.
-- Stop CloudNext writers first. Set @cloudnext_namespace_tenant to the verified Form tenant ID.
-- The guard refuses a wrong database, mixed tenants, or an already/partially migrated schema.
-- Existing IDs, canonical keys, storage keys and file contents are preserved.
DELIMITER $$
CREATE PROCEDURE cloudnext_apply_drive_namespaces_v2()
SQL SECURITY INVOKER
BEGIN
  DECLARE namespace_value VARCHAR(64);
  DECLARE mismatch_count INT DEFAULT 0;


  IF DATABASE() IS NULL OR DATABASE() NOT IN ('qingshancloud', 'qingshancloud_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Refusing namespace migration outside the dedicated CloudNext database';
  END IF;
  IF @cloudnext_namespace_tenant IS NULL OR CHAR_LENGTH(@cloudnext_namespace_tenant) = 0 OR CHAR_LENGTH(@cloudnext_namespace_tenant) > 128 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Set the verified Form tenant ID before applying';
  END IF;
  SELECT COUNT(*) INTO mismatch_count FROM (
    SELECT tenant_id FROM cloudnext_principal_binding WHERE tenant_id IS NOT NULL AND tenant_id <> ''
    UNION SELECT tenant_id FROM cloudnext_auth_session WHERE tenant_id IS NOT NULL AND tenant_id <> ''
    UNION SELECT snapshot_tenant_id FROM cloudnext_directory_sync_state WHERE snapshot_tenant_id IS NOT NULL AND snapshot_tenant_id <> ''
  ) tenants WHERE BINARY tenants.tenant_id <> BINARY @cloudnext_namespace_tenant;
  IF mismatch_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Mixed or mismatching Form tenants require a separate migration';
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'cloudnext_drive_namespace')
    OR EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND column_name = 'drive_id' AND table_name LIKE 'cloudnext_%') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Namespace migration already started; inspect before any resume';
  END IF;

  CREATE TABLE cloudnext_drive_namespace (
    id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    entry_id VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_drive_namespace_entry (tenant_id, entry_id)
  ) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
  SET namespace_value = SHA2(CONCAT('cloudnext:', @cloudnext_namespace_tenant, ':default'), 256);
  INSERT INTO cloudnext_drive_namespace(id, tenant_id, entry_id) VALUES(namespace_value, @cloudnext_namespace_tenant, 'default');

  ALTER TABLE cloudnext_drive_item ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_item SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_item MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_item(drive_id);
  ALTER TABLE cloudnext_drive_share ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_share SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_share MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_share(drive_id);
  ALTER TABLE cloudnext_drive_public_link ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_public_link SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_public_link MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_public_link(drive_id);
  ALTER TABLE cloudnext_workspace ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_workspace SET drive_id = namespace_value;
  ALTER TABLE cloudnext_workspace MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_workspace(drive_id);
  ALTER TABLE cloudnext_workspace_member ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_workspace_member SET drive_id = namespace_value;
  ALTER TABLE cloudnext_workspace_member MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_workspace_member(drive_id);
  ALTER TABLE cloudnext_user_preference ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_user_preference SET drive_id = namespace_value;
  ALTER TABLE cloudnext_user_preference MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_user_preference(drive_id);
  ALTER TABLE cloudnext_audit_log ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_audit_log SET drive_id = namespace_value;
  ALTER TABLE cloudnext_audit_log MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_audit_log(drive_id);
  ALTER TABLE cloudnext_drive_public_link_access_log ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_public_link_access_log SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_public_link_access_log MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_public_link_access_log(drive_id);
  ALTER TABLE cloudnext_drive_user_item ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_user_item SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_user_item MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_user_item(drive_id);
  ALTER TABLE cloudnext_drive_item_version ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_item_version SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_item_version MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_item_version(drive_id);
  ALTER TABLE cloudnext_drive_write_receipt ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_drive_write_receipt SET drive_id = namespace_value;
  ALTER TABLE cloudnext_drive_write_receipt MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_drive_write_receipt(drive_id);
  ALTER TABLE cloudnext_storage_cleanup ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_storage_cleanup SET drive_id = namespace_value;
  ALTER TABLE cloudnext_storage_cleanup MODIFY COLUMN drive_id VARCHAR(64) NOT NULL, ADD INDEX idx_namespace_storage_cleanup(drive_id);
  ALTER TABLE cloudnext_auth_session ADD COLUMN drive_id VARCHAR(64) NULL;
  UPDATE cloudnext_auth_session SET drive_id = namespace_value;
  ALTER TABLE cloudnext_auth_session MODIFY COLUMN drive_id VARCHAR(64) NOT NULL;
  CREATE INDEX idx_drive_namespace_scope ON cloudnext_drive_item(drive_id, scope_type, scope_id, deleted, parent_id, updated_at);
  CREATE INDEX idx_drive_namespace_trash ON cloudnext_drive_item(drive_id, owner_person_oa_id, deleted, deleted_at);
  CREATE INDEX idx_drive_namespace_recent ON cloudnext_drive_item(drive_id, owner_person_oa_id, deleted, last_accessed_at);
  CREATE INDEX idx_share_namespace_target ON cloudnext_drive_share(drive_id, target_type, target_id, revoked, expires_at);
  ALTER TABLE cloudnext_user_preference DROP INDEX uk_cloudnext_user_preference,
    ADD UNIQUE KEY uk_cloudnext_user_preference(drive_id, person_oa_id, preference_key);
END$$
DELIMITER ;
CALL cloudnext_apply_drive_namespaces_v2();
DROP PROCEDURE cloudnext_apply_drive_namespaces_v2;
