-- Synthetic fixture only; loaded into a newly created disposable V18 database.
INSERT INTO users (id,email,password_hash,display_name,display_name_key)
VALUES ('upgrade-owner','upgrade@example.test','synthetic-not-a-login-hash','Synthetic Owner','synthetic owner');
INSERT INTO knowledge_domains (id,owner_id,name) VALUES ('upgrade-domain','upgrade-owner','Synthetic folder');
INSERT INTO markdown_documents (id,owner_id,domain_id,title,file_name,content,version)
VALUES ('upgrade-article','upgrade-owner','upgrade-domain','保留文章','article.md','# Retained article 中文',7);
INSERT INTO markdown_document_revisions (id,document_id,owner_id,document_version,action,title,file_name,content,domain_id,is_favorite,created_at,updated_at)
VALUES ('upgrade-revision','upgrade-article','upgrade-owner',7,'UPDATED','保留文章','article.md','# Retained article 中文','upgrade-domain',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO code_snippets (id,owner_id,domain_id,title,language,code,version)
VALUES ('upgrade-snippet','upgrade-owner','upgrade-domain','Retained snippet','SQL','select ''保留'';',4);
INSERT INTO dev_logs (id,owner_id,domain_id,title,content,category,deleted_at,sharing_generation)
VALUES ('upgrade-log-active','upgrade-owner','upgrade-domain','Active legacy log','Synthetic active log body','DECISION',NULL,0),
       ('upgrade-log-trash','upgrade-owner','upgrade-domain','Trashed legacy log','Synthetic trashed log body','LEARNING',CURRENT_TIMESTAMP,1);
INSERT INTO dev_log_tags (log_id,sort_order,tag)
VALUES ('upgrade-log-active',0,'synthetic'),('upgrade-log-trash',0,'trash');
INSERT INTO markdown_share_links (id,document_id,owner_id,token_digest,expires_at)
VALUES ('upgrade-article-share','upgrade-article','upgrade-owner',repeat('a',64),CURRENT_TIMESTAMP+INTERVAL '30 days');
INSERT INTO knowledge_share_links (id,resource_type,resource_id,owner_id,token_digest,expires_at,resource_generation)
VALUES ('upgrade-snippet-share','SNIPPET','upgrade-snippet','upgrade-owner',repeat('b',64),CURRENT_TIMESTAMP+INTERVAL '30 days',0),
       ('upgrade-log-active-share','DEV_LOG','upgrade-log-active','upgrade-owner',repeat('c',64),CURRENT_TIMESTAMP+INTERVAL '30 days',0),
       ('upgrade-log-trash-share','DEV_LOG','upgrade-log-trash','upgrade-owner',repeat('d',64),CURRENT_TIMESTAMP+INTERVAL '30 days',0);
INSERT INTO sharing_pool_entries (id,owner_id,resource_type,resource_id,resource_generation,shared_at)
VALUES ('pool-article','upgrade-owner','MARKDOWN','upgrade-article',0,CURRENT_TIMESTAMP),
       ('pool-snippet','upgrade-owner','SNIPPET','upgrade-snippet',0,CURRENT_TIMESTAMP),
       ('pool-log-active','upgrade-owner','DEV_LOG','upgrade-log-active',0,CURRENT_TIMESTAMP),
       ('pool-log-trash','upgrade-owner','DEV_LOG','upgrade-log-trash',0,CURRENT_TIMESTAMP);
INSERT INTO share_bundles (id,owner_id,title,token_digest,expires_at,revoked_at)
VALUES ('bundle-mixed','upgrade-owner','Mixed synthetic bundle',repeat('e',64),CURRENT_TIMESTAMP+INTERVAL '30 days',NULL),
       ('bundle-logs','upgrade-owner','Log-only synthetic bundle',repeat('f',64),CURRENT_TIMESTAMP+INTERVAL '30 days',NULL),
       ('bundle-revoked','upgrade-owner','Already revoked log-only bundle',repeat('1',64),CURRENT_TIMESTAMP+INTERVAL '30 days','2025-01-01T00:00:00Z');
INSERT INTO share_bundle_items (id,bundle_id,resource_type,resource_id,resource_generation,original_title,sort_order)
VALUES ('mixed-article','bundle-mixed','MARKDOWN','upgrade-article',0,'保留文章',0),
       ('mixed-log-active','bundle-mixed','DEV_LOG','upgrade-log-active',0,'Active legacy log',1),
       ('mixed-snippet','bundle-mixed','SNIPPET','upgrade-snippet',0,'Retained snippet',2),
       ('mixed-log-trash','bundle-mixed','DEV_LOG','upgrade-log-trash',0,'Trashed legacy log',3),
       ('only-log-active','bundle-logs','DEV_LOG','upgrade-log-active',0,'Active legacy log',0),
       ('only-log-trash','bundle-logs','DEV_LOG','upgrade-log-trash',0,'Trashed legacy log',1),
       ('revoked-log','bundle-revoked','DEV_LOG','upgrade-log-active',0,'Active legacy log',0);
INSERT INTO admin_audit_events (id,actor_id,actor_email,action,resource_type,http_method,request_path,response_status,success)
VALUES ('upgrade-audit','upgrade-owner','upgrade@example.test','MEMBER_WORKSPACE_WRITE','logs','POST','/api/v1/logs',201,true);
