CREATE TABLE scene (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by VARCHAR(64) NOT NULL DEFAULT '系统',
    updated_by VARCHAR(64) NOT NULL DEFAULT '系统',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_scene_name UNIQUE (name)
);

CREATE TABLE view_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by VARCHAR(64) NOT NULL DEFAULT '系统',
    updated_by VARCHAR(64) NOT NULL DEFAULT '系统',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_view_definition_name UNIQUE (name)
);

CREATE TABLE regex_fragment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL,
    pattern_text TEXT NOT NULL,
    is_common BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(64) NOT NULL DEFAULT '系统',
    updated_by VARCHAR(64) NOT NULL DEFAULT '系统',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_regex_fragment_name UNIQUE (name)
);

CREATE TABLE command_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expression_html TEXT NOT NULL,
    expression_text VARCHAR(1000) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    regex_template TEXT NOT NULL,
    match_start BOOLEAN NOT NULL DEFAULT TRUE,
    match_end BOOLEAN NOT NULL DEFAULT TRUE,
    target_view_id BIGINT NULL,
    created_by VARCHAR(64) NOT NULL DEFAULT '系统',
    updated_by VARCHAR(64) NOT NULL DEFAULT '系统',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_command_target_view FOREIGN KEY (target_view_id) REFERENCES view_definition(id)
);

CREATE TABLE command_audit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_display_name VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason VARCHAR(500) NOT NULL,
    changed_fields TEXT NOT NULL,
    before_snapshot TEXT NULL,
    after_snapshot TEXT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'WEB'
);

CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role_name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

CREATE TABLE command_scene (
    command_id BIGINT NOT NULL,
    scene_id BIGINT NOT NULL,
    PRIMARY KEY (command_id, scene_id),
    CONSTRAINT fk_command_scene_command FOREIGN KEY (command_id) REFERENCES command_rule(id) ON DELETE CASCADE,
    CONSTRAINT fk_command_scene_scene FOREIGN KEY (scene_id) REFERENCES scene(id)
);

CREATE TABLE command_current_view (
    command_id BIGINT NOT NULL,
    view_id BIGINT NOT NULL,
    PRIMARY KEY (command_id, view_id),
    CONSTRAINT fk_command_view_command FOREIGN KEY (command_id) REFERENCES command_rule(id) ON DELETE CASCADE,
    CONSTRAINT fk_command_view_view FOREIGN KEY (view_id) REFERENCES view_definition(id)
);

CREATE INDEX idx_command_target_view ON command_rule(target_view_id);
CREATE INDEX idx_command_audit_command_time ON command_audit_event(command_id, occurred_at);
CREATE INDEX idx_command_audit_actor_time ON command_audit_event(actor_username, occurred_at);
CREATE INDEX idx_command_scene_scene ON command_scene(scene_id);
CREATE INDEX idx_command_current_view_view ON command_current_view(view_id);
CREATE INDEX idx_app_user_role ON app_user(role_name);
