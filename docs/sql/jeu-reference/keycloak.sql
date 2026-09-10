--
-- PostgreSQL database dump
--

\restrict 1YGfZC1sntbjlr09pJvww3m7U5KnopS9LeR9BNTZNTFxQimgHcRfPX2Hkw8ZpgQ

-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_event_entity; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.admin_event_entity (
    id character varying(36) NOT NULL,
    admin_event_time bigint,
    realm_id character varying(255),
    operation_type character varying(255),
    auth_realm_id character varying(255),
    auth_client_id character varying(255),
    auth_user_id character varying(255),
    ip_address character varying(255),
    resource_path character varying(2550),
    representation text,
    error character varying(255),
    resource_type character varying(64),
    details_json text
);


ALTER TABLE public.admin_event_entity OWNER TO keycloak;

--
-- Name: associated_policy; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.associated_policy (
    policy_id character varying(36) NOT NULL,
    associated_policy_id character varying(36) NOT NULL
);


ALTER TABLE public.associated_policy OWNER TO keycloak;

--
-- Name: authentication_execution; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.authentication_execution (
    id character varying(36) NOT NULL,
    alias character varying(255),
    authenticator character varying(36),
    realm_id character varying(36),
    flow_id character varying(36),
    requirement integer,
    priority integer,
    authenticator_flow boolean DEFAULT false NOT NULL,
    auth_flow_id character varying(36),
    auth_config character varying(36)
);


ALTER TABLE public.authentication_execution OWNER TO keycloak;

--
-- Name: authentication_flow; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.authentication_flow (
    id character varying(36) NOT NULL,
    alias character varying(255),
    description character varying(255),
    realm_id character varying(36),
    provider_id character varying(36) DEFAULT 'basic-flow'::character varying NOT NULL,
    top_level boolean DEFAULT false NOT NULL,
    built_in boolean DEFAULT false NOT NULL
);


ALTER TABLE public.authentication_flow OWNER TO keycloak;

--
-- Name: authenticator_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.authenticator_config (
    id character varying(36) NOT NULL,
    alias character varying(255),
    realm_id character varying(36)
);


ALTER TABLE public.authenticator_config OWNER TO keycloak;

--
-- Name: authenticator_config_entry; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.authenticator_config_entry (
    authenticator_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.authenticator_config_entry OWNER TO keycloak;

--
-- Name: broker_link; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.broker_link (
    identity_provider character varying(255) NOT NULL,
    storage_provider_id character varying(255),
    realm_id character varying(36) NOT NULL,
    broker_user_id character varying(255),
    broker_username character varying(255),
    token text,
    user_id character varying(255) NOT NULL
);


ALTER TABLE public.broker_link OWNER TO keycloak;

--
-- Name: client; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client (
    id character varying(36) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    full_scope_allowed boolean DEFAULT false NOT NULL,
    client_id character varying(255),
    not_before integer,
    public_client boolean DEFAULT false NOT NULL,
    secret character varying(255),
    base_url character varying(255),
    bearer_only boolean DEFAULT false NOT NULL,
    management_url character varying(255),
    surrogate_auth_required boolean DEFAULT false NOT NULL,
    realm_id character varying(36),
    protocol character varying(255),
    node_rereg_timeout integer DEFAULT 0,
    frontchannel_logout boolean DEFAULT false NOT NULL,
    consent_required boolean DEFAULT false NOT NULL,
    name character varying(255),
    service_accounts_enabled boolean DEFAULT false NOT NULL,
    client_authenticator_type character varying(255),
    root_url character varying(255),
    description character varying(255),
    registration_token character varying(255),
    standard_flow_enabled boolean DEFAULT true NOT NULL,
    implicit_flow_enabled boolean DEFAULT false NOT NULL,
    direct_access_grants_enabled boolean DEFAULT false NOT NULL,
    always_display_in_console boolean DEFAULT false NOT NULL
);


ALTER TABLE public.client OWNER TO keycloak;

--
-- Name: client_attributes; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_attributes (
    client_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.client_attributes OWNER TO keycloak;

--
-- Name: client_auth_flow_bindings; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_auth_flow_bindings (
    client_id character varying(36) NOT NULL,
    flow_id character varying(36),
    binding_name character varying(255) NOT NULL
);


ALTER TABLE public.client_auth_flow_bindings OWNER TO keycloak;

--
-- Name: client_initial_access; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_initial_access (
    id character varying(36) NOT NULL,
    realm_id character varying(36) NOT NULL,
    "timestamp" integer,
    expiration integer,
    count integer,
    remaining_count integer
);


ALTER TABLE public.client_initial_access OWNER TO keycloak;

--
-- Name: client_node_registrations; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_node_registrations (
    client_id character varying(36) NOT NULL,
    value integer,
    name character varying(255) NOT NULL
);


ALTER TABLE public.client_node_registrations OWNER TO keycloak;

--
-- Name: client_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_scope (
    id character varying(36) NOT NULL,
    name character varying(255),
    realm_id character varying(36),
    description character varying(255),
    protocol character varying(255)
);


ALTER TABLE public.client_scope OWNER TO keycloak;

--
-- Name: client_scope_attributes; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_scope_attributes (
    scope_id character varying(36) NOT NULL,
    value character varying(2048),
    name character varying(255) NOT NULL
);


ALTER TABLE public.client_scope_attributes OWNER TO keycloak;

--
-- Name: client_scope_client; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_scope_client (
    client_id character varying(255) NOT NULL,
    scope_id character varying(255) NOT NULL,
    default_scope boolean DEFAULT false NOT NULL
);


ALTER TABLE public.client_scope_client OWNER TO keycloak;

--
-- Name: client_scope_role_mapping; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.client_scope_role_mapping (
    scope_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);


ALTER TABLE public.client_scope_role_mapping OWNER TO keycloak;

--
-- Name: component; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.component (
    id character varying(36) NOT NULL,
    name character varying(255),
    parent_id character varying(36),
    provider_id character varying(36),
    provider_type character varying(255),
    realm_id character varying(36),
    sub_type character varying(255)
);


ALTER TABLE public.component OWNER TO keycloak;

--
-- Name: component_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.component_config (
    id character varying(36) NOT NULL,
    component_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.component_config OWNER TO keycloak;

--
-- Name: composite_role; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.composite_role (
    composite character varying(36) NOT NULL,
    child_role character varying(36) NOT NULL
);


ALTER TABLE public.composite_role OWNER TO keycloak;

--
-- Name: credential; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.credential (
    id character varying(36) NOT NULL,
    salt bytea,
    type character varying(255),
    user_id character varying(36),
    created_date bigint,
    user_label character varying(255),
    secret_data text,
    credential_data text,
    priority integer
);


ALTER TABLE public.credential OWNER TO keycloak;

--
-- Name: databasechangelog; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);


ALTER TABLE public.databasechangelog OWNER TO keycloak;

--
-- Name: databasechangeloglock; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);


ALTER TABLE public.databasechangeloglock OWNER TO keycloak;

--
-- Name: default_client_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.default_client_scope (
    realm_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL,
    default_scope boolean DEFAULT false NOT NULL
);


ALTER TABLE public.default_client_scope OWNER TO keycloak;

--
-- Name: event_entity; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.event_entity (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    details_json character varying(2550),
    error character varying(255),
    ip_address character varying(255),
    realm_id character varying(255),
    session_id character varying(255),
    event_time bigint,
    type character varying(255),
    user_id character varying(255),
    details_json_long_value text
);


ALTER TABLE public.event_entity OWNER TO keycloak;

--
-- Name: fed_user_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_attribute (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    value character varying(2024),
    long_value_hash bytea,
    long_value_hash_lower_case bytea,
    long_value text
);


ALTER TABLE public.fed_user_attribute OWNER TO keycloak;

--
-- Name: fed_user_consent; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_consent (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    created_date bigint,
    last_updated_date bigint,
    client_storage_provider character varying(36),
    external_client_id character varying(255)
);


ALTER TABLE public.fed_user_consent OWNER TO keycloak;

--
-- Name: fed_user_consent_cl_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_consent_cl_scope (
    user_consent_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.fed_user_consent_cl_scope OWNER TO keycloak;

--
-- Name: fed_user_credential; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_credential (
    id character varying(36) NOT NULL,
    salt bytea,
    type character varying(255),
    created_date bigint,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36),
    user_label character varying(255),
    secret_data text,
    credential_data text,
    priority integer
);


ALTER TABLE public.fed_user_credential OWNER TO keycloak;

--
-- Name: fed_user_group_membership; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_group_membership (
    group_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_group_membership OWNER TO keycloak;

--
-- Name: fed_user_required_action; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_required_action (
    required_action character varying(255) DEFAULT ' '::character varying NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_required_action OWNER TO keycloak;

--
-- Name: fed_user_role_mapping; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.fed_user_role_mapping (
    role_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    storage_provider_id character varying(36)
);


ALTER TABLE public.fed_user_role_mapping OWNER TO keycloak;

--
-- Name: federated_identity; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.federated_identity (
    identity_provider character varying(255) NOT NULL,
    realm_id character varying(36),
    federated_user_id character varying(255),
    federated_username character varying(255),
    token text,
    user_id character varying(36) NOT NULL
);


ALTER TABLE public.federated_identity OWNER TO keycloak;

--
-- Name: federated_user; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.federated_user (
    id character varying(255) NOT NULL,
    storage_provider_id character varying(255),
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.federated_user OWNER TO keycloak;

--
-- Name: group_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.group_attribute (
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255),
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.group_attribute OWNER TO keycloak;

--
-- Name: group_role_mapping; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.group_role_mapping (
    role_id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.group_role_mapping OWNER TO keycloak;

--
-- Name: identity_provider; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.identity_provider (
    internal_id character varying(36) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    provider_alias character varying(255),
    provider_id character varying(255),
    store_token boolean DEFAULT false NOT NULL,
    authenticate_by_default boolean DEFAULT false NOT NULL,
    realm_id character varying(36),
    add_token_role boolean DEFAULT true NOT NULL,
    trust_email boolean DEFAULT false NOT NULL,
    first_broker_login_flow_id character varying(36),
    post_broker_login_flow_id character varying(36),
    provider_display_name character varying(255),
    link_only boolean DEFAULT false NOT NULL,
    organization_id character varying(255),
    hide_on_login boolean DEFAULT false
);


ALTER TABLE public.identity_provider OWNER TO keycloak;

--
-- Name: identity_provider_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.identity_provider_config (
    identity_provider_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.identity_provider_config OWNER TO keycloak;

--
-- Name: identity_provider_mapper; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.identity_provider_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    idp_alias character varying(255) NOT NULL,
    idp_mapper_name character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.identity_provider_mapper OWNER TO keycloak;

--
-- Name: idp_mapper_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.idp_mapper_config (
    idp_mapper_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.idp_mapper_config OWNER TO keycloak;

--
-- Name: keycloak_group; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.keycloak_group (
    id character varying(36) NOT NULL,
    name character varying(255),
    parent_group character varying(36) NOT NULL,
    realm_id character varying(36),
    type integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.keycloak_group OWNER TO keycloak;

--
-- Name: keycloak_role; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.keycloak_role (
    id character varying(36) NOT NULL,
    client_realm_constraint character varying(255),
    client_role boolean DEFAULT false NOT NULL,
    description character varying(255),
    name character varying(255),
    realm_id character varying(255),
    client character varying(36),
    realm character varying(36)
);


ALTER TABLE public.keycloak_role OWNER TO keycloak;

--
-- Name: migration_model; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.migration_model (
    id character varying(36) NOT NULL,
    version character varying(36),
    update_time bigint DEFAULT 0 NOT NULL
);


ALTER TABLE public.migration_model OWNER TO keycloak;

--
-- Name: offline_client_session; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.offline_client_session (
    user_session_id character varying(36) NOT NULL,
    client_id character varying(255) NOT NULL,
    offline_flag character varying(4) NOT NULL,
    "timestamp" integer,
    data text,
    client_storage_provider character varying(36) DEFAULT 'local'::character varying NOT NULL,
    external_client_id character varying(255) DEFAULT 'local'::character varying NOT NULL,
    version integer DEFAULT 0
);


ALTER TABLE public.offline_client_session OWNER TO keycloak;

--
-- Name: offline_user_session; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.offline_user_session (
    user_session_id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    created_on integer NOT NULL,
    offline_flag character varying(4) NOT NULL,
    data text,
    last_session_refresh integer DEFAULT 0 NOT NULL,
    broker_session_id character varying(1024),
    version integer DEFAULT 0
);


ALTER TABLE public.offline_user_session OWNER TO keycloak;

--
-- Name: org; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.org (
    id character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    realm_id character varying(255) NOT NULL,
    group_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(4000),
    alias character varying(255) NOT NULL,
    redirect_url character varying(2048)
);


ALTER TABLE public.org OWNER TO keycloak;

--
-- Name: org_domain; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.org_domain (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    verified boolean NOT NULL,
    org_id character varying(255) NOT NULL
);


ALTER TABLE public.org_domain OWNER TO keycloak;

--
-- Name: policy_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.policy_config (
    policy_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value text
);


ALTER TABLE public.policy_config OWNER TO keycloak;

--
-- Name: protocol_mapper; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.protocol_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    protocol character varying(255) NOT NULL,
    protocol_mapper_name character varying(255) NOT NULL,
    client_id character varying(36),
    client_scope_id character varying(36)
);


ALTER TABLE public.protocol_mapper OWNER TO keycloak;

--
-- Name: protocol_mapper_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.protocol_mapper_config (
    protocol_mapper_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.protocol_mapper_config OWNER TO keycloak;

--
-- Name: realm; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm (
    id character varying(36) NOT NULL,
    access_code_lifespan integer,
    user_action_lifespan integer,
    access_token_lifespan integer,
    account_theme character varying(255),
    admin_theme character varying(255),
    email_theme character varying(255),
    enabled boolean DEFAULT false NOT NULL,
    events_enabled boolean DEFAULT false NOT NULL,
    events_expiration bigint,
    login_theme character varying(255),
    name character varying(255),
    not_before integer,
    password_policy character varying(2550),
    registration_allowed boolean DEFAULT false NOT NULL,
    remember_me boolean DEFAULT false NOT NULL,
    reset_password_allowed boolean DEFAULT false NOT NULL,
    social boolean DEFAULT false NOT NULL,
    ssl_required character varying(255),
    sso_idle_timeout integer,
    sso_max_lifespan integer,
    update_profile_on_soc_login boolean DEFAULT false NOT NULL,
    verify_email boolean DEFAULT false NOT NULL,
    master_admin_client character varying(36),
    login_lifespan integer,
    internationalization_enabled boolean DEFAULT false NOT NULL,
    default_locale character varying(255),
    reg_email_as_username boolean DEFAULT false NOT NULL,
    admin_events_enabled boolean DEFAULT false NOT NULL,
    admin_events_details_enabled boolean DEFAULT false NOT NULL,
    edit_username_allowed boolean DEFAULT false NOT NULL,
    otp_policy_counter integer DEFAULT 0,
    otp_policy_window integer DEFAULT 1,
    otp_policy_period integer DEFAULT 30,
    otp_policy_digits integer DEFAULT 6,
    otp_policy_alg character varying(36) DEFAULT 'HmacSHA1'::character varying,
    otp_policy_type character varying(36) DEFAULT 'totp'::character varying,
    browser_flow character varying(36),
    registration_flow character varying(36),
    direct_grant_flow character varying(36),
    reset_credentials_flow character varying(36),
    client_auth_flow character varying(36),
    offline_session_idle_timeout integer DEFAULT 0,
    revoke_refresh_token boolean DEFAULT false NOT NULL,
    access_token_life_implicit integer DEFAULT 0,
    login_with_email_allowed boolean DEFAULT true NOT NULL,
    duplicate_emails_allowed boolean DEFAULT false NOT NULL,
    docker_auth_flow character varying(36),
    refresh_token_max_reuse integer DEFAULT 0,
    allow_user_managed_access boolean DEFAULT false NOT NULL,
    sso_max_lifespan_remember_me integer DEFAULT 0 NOT NULL,
    sso_idle_timeout_remember_me integer DEFAULT 0 NOT NULL,
    default_role character varying(255)
);


ALTER TABLE public.realm OWNER TO keycloak;

--
-- Name: realm_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_attribute (
    name character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL,
    value text
);


ALTER TABLE public.realm_attribute OWNER TO keycloak;

--
-- Name: realm_default_groups; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_default_groups (
    realm_id character varying(36) NOT NULL,
    group_id character varying(36) NOT NULL
);


ALTER TABLE public.realm_default_groups OWNER TO keycloak;

--
-- Name: realm_enabled_event_types; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_enabled_event_types (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_enabled_event_types OWNER TO keycloak;

--
-- Name: realm_events_listeners; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_events_listeners (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_events_listeners OWNER TO keycloak;

--
-- Name: realm_localizations; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_localizations (
    realm_id character varying(255) NOT NULL,
    locale character varying(255) NOT NULL,
    texts text NOT NULL
);


ALTER TABLE public.realm_localizations OWNER TO keycloak;

--
-- Name: realm_required_credential; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_required_credential (
    type character varying(255) NOT NULL,
    form_label character varying(255),
    input boolean DEFAULT false NOT NULL,
    secret boolean DEFAULT false NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.realm_required_credential OWNER TO keycloak;

--
-- Name: realm_smtp_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_smtp_config (
    realm_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.realm_smtp_config OWNER TO keycloak;

--
-- Name: realm_supported_locales; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.realm_supported_locales (
    realm_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.realm_supported_locales OWNER TO keycloak;

--
-- Name: redirect_uris; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.redirect_uris (
    client_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.redirect_uris OWNER TO keycloak;

--
-- Name: required_action_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.required_action_config (
    required_action_id character varying(36) NOT NULL,
    value text,
    name character varying(255) NOT NULL
);


ALTER TABLE public.required_action_config OWNER TO keycloak;

--
-- Name: required_action_provider; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.required_action_provider (
    id character varying(36) NOT NULL,
    alias character varying(255),
    name character varying(255),
    realm_id character varying(36),
    enabled boolean DEFAULT false NOT NULL,
    default_action boolean DEFAULT false NOT NULL,
    provider_id character varying(255),
    priority integer
);


ALTER TABLE public.required_action_provider OWNER TO keycloak;

--
-- Name: resource_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_attribute (
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255),
    resource_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_attribute OWNER TO keycloak;

--
-- Name: resource_policy; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_policy (
    resource_id character varying(36) NOT NULL,
    policy_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_policy OWNER TO keycloak;

--
-- Name: resource_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_scope (
    resource_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.resource_scope OWNER TO keycloak;

--
-- Name: resource_server; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_server (
    id character varying(36) NOT NULL,
    allow_rs_remote_mgmt boolean DEFAULT false NOT NULL,
    policy_enforce_mode smallint NOT NULL,
    decision_strategy smallint DEFAULT 1 NOT NULL
);


ALTER TABLE public.resource_server OWNER TO keycloak;

--
-- Name: resource_server_perm_ticket; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_server_perm_ticket (
    id character varying(36) NOT NULL,
    owner character varying(255) NOT NULL,
    requester character varying(255) NOT NULL,
    created_timestamp bigint NOT NULL,
    granted_timestamp bigint,
    resource_id character varying(36) NOT NULL,
    scope_id character varying(36),
    resource_server_id character varying(36) NOT NULL,
    policy_id character varying(36)
);


ALTER TABLE public.resource_server_perm_ticket OWNER TO keycloak;

--
-- Name: resource_server_policy; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_server_policy (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    type character varying(255) NOT NULL,
    decision_strategy smallint,
    logic smallint,
    resource_server_id character varying(36) NOT NULL,
    owner character varying(255)
);


ALTER TABLE public.resource_server_policy OWNER TO keycloak;

--
-- Name: resource_server_resource; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_server_resource (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255),
    icon_uri character varying(255),
    owner character varying(255) NOT NULL,
    resource_server_id character varying(36) NOT NULL,
    owner_managed_access boolean DEFAULT false NOT NULL,
    display_name character varying(255)
);


ALTER TABLE public.resource_server_resource OWNER TO keycloak;

--
-- Name: resource_server_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_server_scope (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    icon_uri character varying(255),
    resource_server_id character varying(36) NOT NULL,
    display_name character varying(255)
);


ALTER TABLE public.resource_server_scope OWNER TO keycloak;

--
-- Name: resource_uris; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.resource_uris (
    resource_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.resource_uris OWNER TO keycloak;

--
-- Name: revoked_token; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.revoked_token (
    id character varying(255) NOT NULL,
    expire bigint NOT NULL
);


ALTER TABLE public.revoked_token OWNER TO keycloak;

--
-- Name: role_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.role_attribute (
    id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    value character varying(255)
);


ALTER TABLE public.role_attribute OWNER TO keycloak;

--
-- Name: scope_mapping; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.scope_mapping (
    client_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);


ALTER TABLE public.scope_mapping OWNER TO keycloak;

--
-- Name: scope_policy; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.scope_policy (
    scope_id character varying(36) NOT NULL,
    policy_id character varying(36) NOT NULL
);


ALTER TABLE public.scope_policy OWNER TO keycloak;

--
-- Name: user_attribute; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_attribute (
    name character varying(255) NOT NULL,
    value character varying(255),
    user_id character varying(36) NOT NULL,
    id character varying(36) DEFAULT 'sybase-needs-something-here'::character varying NOT NULL,
    long_value_hash bytea,
    long_value_hash_lower_case bytea,
    long_value text
);


ALTER TABLE public.user_attribute OWNER TO keycloak;

--
-- Name: user_consent; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_consent (
    id character varying(36) NOT NULL,
    client_id character varying(255),
    user_id character varying(36) NOT NULL,
    created_date bigint,
    last_updated_date bigint,
    client_storage_provider character varying(36),
    external_client_id character varying(255)
);


ALTER TABLE public.user_consent OWNER TO keycloak;

--
-- Name: user_consent_client_scope; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_consent_client_scope (
    user_consent_id character varying(36) NOT NULL,
    scope_id character varying(36) NOT NULL
);


ALTER TABLE public.user_consent_client_scope OWNER TO keycloak;

--
-- Name: user_entity; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_entity (
    id character varying(36) NOT NULL,
    email character varying(255),
    email_constraint character varying(255),
    email_verified boolean DEFAULT false NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    federation_link character varying(255),
    first_name character varying(255),
    last_name character varying(255),
    realm_id character varying(255),
    username character varying(255),
    created_timestamp bigint,
    service_account_client_link character varying(255),
    not_before integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.user_entity OWNER TO keycloak;

--
-- Name: user_federation_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_federation_config (
    user_federation_provider_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.user_federation_config OWNER TO keycloak;

--
-- Name: user_federation_mapper; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_federation_mapper (
    id character varying(36) NOT NULL,
    name character varying(255) NOT NULL,
    federation_provider_id character varying(36) NOT NULL,
    federation_mapper_type character varying(255) NOT NULL,
    realm_id character varying(36) NOT NULL
);


ALTER TABLE public.user_federation_mapper OWNER TO keycloak;

--
-- Name: user_federation_mapper_config; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_federation_mapper_config (
    user_federation_mapper_id character varying(36) NOT NULL,
    value character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.user_federation_mapper_config OWNER TO keycloak;

--
-- Name: user_federation_provider; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_federation_provider (
    id character varying(36) NOT NULL,
    changed_sync_period integer,
    display_name character varying(255),
    full_sync_period integer,
    last_sync integer,
    priority integer,
    provider_name character varying(255),
    realm_id character varying(36)
);


ALTER TABLE public.user_federation_provider OWNER TO keycloak;

--
-- Name: user_group_membership; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_group_membership (
    group_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    membership_type character varying(255) NOT NULL
);


ALTER TABLE public.user_group_membership OWNER TO keycloak;

--
-- Name: user_required_action; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_required_action (
    user_id character varying(36) NOT NULL,
    required_action character varying(255) DEFAULT ' '::character varying NOT NULL
);


ALTER TABLE public.user_required_action OWNER TO keycloak;

--
-- Name: user_role_mapping; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.user_role_mapping (
    role_id character varying(255) NOT NULL,
    user_id character varying(36) NOT NULL
);


ALTER TABLE public.user_role_mapping OWNER TO keycloak;

--
-- Name: username_login_failure; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.username_login_failure (
    realm_id character varying(36) NOT NULL,
    username character varying(255) NOT NULL,
    failed_login_not_before integer,
    last_failure bigint,
    last_ip_failure character varying(255),
    num_failures integer
);


ALTER TABLE public.username_login_failure OWNER TO keycloak;

--
-- Name: web_origins; Type: TABLE; Schema: public; Owner: keycloak
--

CREATE TABLE public.web_origins (
    client_id character varying(36) NOT NULL,
    value character varying(255) NOT NULL
);


ALTER TABLE public.web_origins OWNER TO keycloak;

--
-- Data for Name: admin_event_entity; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.admin_event_entity (id, admin_event_time, realm_id, operation_type, auth_realm_id, auth_client_id, auth_user_id, ip_address, resource_path, representation, error, resource_type, details_json) FROM stdin;
\.


--
-- Data for Name: associated_policy; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.associated_policy (policy_id, associated_policy_id) FROM stdin;
5bb8041c-a9d7-4550-bb01-82fa40c4ac7a	da878d11-f3ea-45ca-9891-e32fb38cfbd0
\.


--
-- Data for Name: authentication_execution; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.authentication_execution (id, alias, authenticator, realm_id, flow_id, requirement, priority, authenticator_flow, auth_flow_id, auth_config) FROM stdin;
cc454c38-41e0-4966-8921-a70b7d67e522	\N	auth-cookie	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8d932c23-9b14-4631-9115-68fb5c1280e6	2	10	f	\N	\N
96dacf7c-67de-4ad0-b556-dafe3ca68e53	\N	auth-spnego	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8d932c23-9b14-4631-9115-68fb5c1280e6	3	20	f	\N	\N
a7e7348a-d8f0-48f2-b6ef-99a756556334	\N	identity-provider-redirector	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8d932c23-9b14-4631-9115-68fb5c1280e6	2	25	f	\N	\N
edecb97d-bc3c-40ad-8ee3-1cca84c01fb8	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8d932c23-9b14-4631-9115-68fb5c1280e6	2	30	t	d9e7f8c7-7698-41fc-8942-3d2f2a8bc524	\N
a1ae39b9-b7e9-45eb-a6dc-3e2ff98e447c	\N	auth-username-password-form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	d9e7f8c7-7698-41fc-8942-3d2f2a8bc524	0	10	f	\N	\N
c2b863f2-eb9a-474c-943d-bd8041072487	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	d9e7f8c7-7698-41fc-8942-3d2f2a8bc524	1	20	t	301872f9-c562-4b04-809c-1d3060737911	\N
fb810053-a32e-46f9-b1ae-0517bcdf97ef	\N	conditional-user-configured	259bfe83-5374-4ae7-bb38-5464d0d5bc75	301872f9-c562-4b04-809c-1d3060737911	0	10	f	\N	\N
4ef252c3-16a7-423e-a400-04488158960a	\N	auth-otp-form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	301872f9-c562-4b04-809c-1d3060737911	0	20	f	\N	\N
923a29d8-03a4-4543-a81a-7d2896ae9a1f	\N	direct-grant-validate-username	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8ea741aa-a19b-4f69-85f2-c44caced0001	0	10	f	\N	\N
3a30bfb6-0510-4123-829a-d64b7d6bb905	\N	direct-grant-validate-password	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8ea741aa-a19b-4f69-85f2-c44caced0001	0	20	f	\N	\N
347146d0-dcd3-4626-a2f4-f5d6d924cf97	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	8ea741aa-a19b-4f69-85f2-c44caced0001	1	30	t	29ff59dc-fe15-4b7e-9e6e-8ef80ef7ce04	\N
81bb63bb-ca8b-4a35-9458-783b7c35f358	\N	conditional-user-configured	259bfe83-5374-4ae7-bb38-5464d0d5bc75	29ff59dc-fe15-4b7e-9e6e-8ef80ef7ce04	0	10	f	\N	\N
bcca7707-9709-4341-94c4-f9297526d34b	\N	direct-grant-validate-otp	259bfe83-5374-4ae7-bb38-5464d0d5bc75	29ff59dc-fe15-4b7e-9e6e-8ef80ef7ce04	0	20	f	\N	\N
c6398ba5-d855-4525-8802-cfbbcb3746d6	\N	registration-page-form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d047957-c675-4bd6-b8b8-ba1388bfe7bc	0	10	t	13e4dd1f-5d2f-4451-bbac-b2830c29dff1	\N
2e1b80f0-b6f7-4fcc-b65d-8b85cf4072a2	\N	registration-user-creation	259bfe83-5374-4ae7-bb38-5464d0d5bc75	13e4dd1f-5d2f-4451-bbac-b2830c29dff1	0	20	f	\N	\N
1af99e1f-b951-4af5-a62b-d5e031080785	\N	registration-password-action	259bfe83-5374-4ae7-bb38-5464d0d5bc75	13e4dd1f-5d2f-4451-bbac-b2830c29dff1	0	50	f	\N	\N
64e2305a-e9d3-4748-8769-f62c8dc9d90f	\N	registration-recaptcha-action	259bfe83-5374-4ae7-bb38-5464d0d5bc75	13e4dd1f-5d2f-4451-bbac-b2830c29dff1	3	60	f	\N	\N
4ab29d0c-ed3c-4c71-b51d-c841657b30ea	\N	registration-terms-and-conditions	259bfe83-5374-4ae7-bb38-5464d0d5bc75	13e4dd1f-5d2f-4451-bbac-b2830c29dff1	3	70	f	\N	\N
6e75fc5a-e748-4a2f-be35-cb1332e64f44	\N	reset-credentials-choose-user	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e21e4d5f-321d-48a6-96b6-91e5c4b140b3	0	10	f	\N	\N
91529715-0d5d-4d99-8c95-39a3c7f9e38a	\N	reset-credential-email	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e21e4d5f-321d-48a6-96b6-91e5c4b140b3	0	20	f	\N	\N
d1d307c3-3546-4a79-a398-c81e64922ba3	\N	reset-password	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e21e4d5f-321d-48a6-96b6-91e5c4b140b3	0	30	f	\N	\N
e29d6e63-e687-4eb2-b25b-c452bd674e6a	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e21e4d5f-321d-48a6-96b6-91e5c4b140b3	1	40	t	e3a43a6c-8d01-4d5d-8309-1e78a8333656	\N
5c4b1fbb-c2b3-47cc-a6bb-e31b7ea5796c	\N	conditional-user-configured	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e3a43a6c-8d01-4d5d-8309-1e78a8333656	0	10	f	\N	\N
73fba3d2-8707-453f-b20e-edd6969f6225	\N	reset-otp	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e3a43a6c-8d01-4d5d-8309-1e78a8333656	0	20	f	\N	\N
b79cd4cc-f2a9-42de-a2c4-5f629fb81d59	\N	client-secret	259bfe83-5374-4ae7-bb38-5464d0d5bc75	2a7e05b5-60dc-4103-92af-1cbca75f5a5f	2	10	f	\N	\N
befdfcb6-ab3d-44d9-ac56-e2da7cbab470	\N	client-jwt	259bfe83-5374-4ae7-bb38-5464d0d5bc75	2a7e05b5-60dc-4103-92af-1cbca75f5a5f	2	20	f	\N	\N
d22bb24d-9a74-4373-9a57-36020348b80c	\N	client-secret-jwt	259bfe83-5374-4ae7-bb38-5464d0d5bc75	2a7e05b5-60dc-4103-92af-1cbca75f5a5f	2	30	f	\N	\N
a3988315-29dd-4639-b1af-089c13b000e8	\N	client-x509	259bfe83-5374-4ae7-bb38-5464d0d5bc75	2a7e05b5-60dc-4103-92af-1cbca75f5a5f	2	40	f	\N	\N
0e05a22b-dbc4-45cb-ae2e-01a871867181	\N	idp-review-profile	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e89f2d94-88af-4e46-9ebd-42ff5d0bed91	0	10	f	\N	b6f57804-c949-4e40-afad-92bdeb8c6e2d
58ad67d8-0ccb-42f1-90b6-06ddf38029d4	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e89f2d94-88af-4e46-9ebd-42ff5d0bed91	0	20	t	835111b5-68bd-45e6-91d9-9fb2d899d16e	\N
98748005-a5f4-433f-ab4a-52a38fa92ffa	\N	idp-create-user-if-unique	259bfe83-5374-4ae7-bb38-5464d0d5bc75	835111b5-68bd-45e6-91d9-9fb2d899d16e	2	10	f	\N	346c4279-8968-4841-b8ac-5c461d6493ea
4044a502-3b4d-4a15-80f4-bc4d550a2484	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	835111b5-68bd-45e6-91d9-9fb2d899d16e	2	20	t	26cf07ec-50b6-4940-a4a5-a13cf322142d	\N
0e8ab647-0903-4e8a-8a74-071239d08fb2	\N	idp-confirm-link	259bfe83-5374-4ae7-bb38-5464d0d5bc75	26cf07ec-50b6-4940-a4a5-a13cf322142d	0	10	f	\N	\N
3cd9387b-7379-4d49-9529-b2b308b3bf23	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	26cf07ec-50b6-4940-a4a5-a13cf322142d	0	20	t	01c28904-fc66-4c5e-b4aa-7de5ed9f712b	\N
ef524eb6-d102-459a-9543-187807b752be	\N	idp-email-verification	259bfe83-5374-4ae7-bb38-5464d0d5bc75	01c28904-fc66-4c5e-b4aa-7de5ed9f712b	2	10	f	\N	\N
75bf9edd-a672-4733-92dd-013cd1800701	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	01c28904-fc66-4c5e-b4aa-7de5ed9f712b	2	20	t	c06eb2d4-a931-4693-b328-6a0c2712a262	\N
758beaf3-e22b-4d72-8084-33a2bbed675a	\N	idp-username-password-form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	c06eb2d4-a931-4693-b328-6a0c2712a262	0	10	f	\N	\N
c97157a0-3d69-40ea-bc75-c3be622f1039	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	c06eb2d4-a931-4693-b328-6a0c2712a262	1	20	t	f233d1fc-f469-43ee-a545-ddd28769df18	\N
750789ba-9e82-4ecb-8334-3eb8a7ed3eca	\N	conditional-user-configured	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f233d1fc-f469-43ee-a545-ddd28769df18	0	10	f	\N	\N
527ef9b3-8b68-4b0d-88f9-c576ad15f667	\N	auth-otp-form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f233d1fc-f469-43ee-a545-ddd28769df18	0	20	f	\N	\N
b7a21e41-7037-4d3c-92cd-9829b2e531e4	\N	http-basic-authenticator	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e3925878-9c24-443d-bc2e-39ae8c9fa370	0	10	f	\N	\N
956bab80-3d03-487a-9e97-1ec30f9cb1a2	\N	docker-http-basic-authenticator	259bfe83-5374-4ae7-bb38-5464d0d5bc75	747a49b9-eea1-4846-b2f5-3a7543b6cb5b	0	10	f	\N	\N
c8d15e9b-d219-408a-8886-40e0671f3758	\N	auth-cookie	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f28f4ca9-ac34-4863-8736-baa41db4d706	2	10	f	\N	\N
a71bb3d3-cfd6-4a94-b6f2-e739bb0d5619	\N	auth-spnego	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f28f4ca9-ac34-4863-8736-baa41db4d706	3	20	f	\N	\N
e4a2f1bb-7227-47be-9fb4-33dce62552d3	\N	identity-provider-redirector	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f28f4ca9-ac34-4863-8736-baa41db4d706	2	25	f	\N	\N
9627c999-1de9-41a0-bbcc-2c8e88431e9f	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f28f4ca9-ac34-4863-8736-baa41db4d706	2	30	t	561d2206-7df3-4b3e-83c2-1963abf9cc2f	\N
4109814b-4c88-4460-bdf8-f62300f045a6	\N	auth-username-password-form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	561d2206-7df3-4b3e-83c2-1963abf9cc2f	0	10	f	\N	\N
fa206209-03f4-430b-9f77-0890d8cedc19	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	561d2206-7df3-4b3e-83c2-1963abf9cc2f	1	20	t	926f38ee-ac84-431c-84b4-b278f1f672da	\N
ce0410a5-fb9c-4ead-aff1-ee64ea5d95c0	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	926f38ee-ac84-431c-84b4-b278f1f672da	0	10	f	\N	\N
97e4ca41-7e86-4411-914c-87fd477dffde	\N	auth-otp-form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	926f38ee-ac84-431c-84b4-b278f1f672da	0	20	f	\N	\N
0b437abd-4b55-4c8d-a465-a5e1a68aab80	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f28f4ca9-ac34-4863-8736-baa41db4d706	2	26	t	094a6276-4ed6-410a-83ed-72a94b565e0c	\N
7538939c-b213-4ab5-9e27-736f3fc297d4	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	094a6276-4ed6-410a-83ed-72a94b565e0c	1	10	t	7131551b-c146-4a72-a415-9f3a0543cbf6	\N
8ad1d7c5-1786-4a7b-aa8f-61b6f5b548ab	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7131551b-c146-4a72-a415-9f3a0543cbf6	0	10	f	\N	\N
4c653e55-ebca-43e7-a519-915cb2a0704f	\N	organization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7131551b-c146-4a72-a415-9f3a0543cbf6	2	20	f	\N	\N
4868b818-0a3f-45c1-af26-cbed747a55c2	\N	direct-grant-validate-username	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c385709a-f0bc-405e-8b86-6502e1bd1709	0	10	f	\N	\N
b4f7fb6d-24ce-40ce-90f1-d8662e72eef6	\N	direct-grant-validate-password	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c385709a-f0bc-405e-8b86-6502e1bd1709	0	20	f	\N	\N
94e9f28f-5625-4938-af1f-bf686095f323	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c385709a-f0bc-405e-8b86-6502e1bd1709	1	30	t	8d620c92-0651-4971-9bf6-2bc3129e272d	\N
e9db5e21-035f-4762-a283-22c17b734742	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	8d620c92-0651-4971-9bf6-2bc3129e272d	0	10	f	\N	\N
199f8d90-df61-45f6-a223-3a53b8bc5ecf	\N	direct-grant-validate-otp	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	8d620c92-0651-4971-9bf6-2bc3129e272d	0	20	f	\N	\N
c4f0cadb-d02b-4ed9-a0c3-845156e4adce	\N	registration-page-form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7ef380b4-c2ce-4684-ac9c-66fcedec973c	0	10	t	d27da98e-448f-4020-9702-c880e6bbab18	\N
e2dcaff7-1b8a-4c53-88c9-b1744a454c5f	\N	registration-user-creation	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d27da98e-448f-4020-9702-c880e6bbab18	0	20	f	\N	\N
b6b8e7ef-3958-42c1-aeb5-3ed56ba5d39d	\N	registration-password-action	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d27da98e-448f-4020-9702-c880e6bbab18	0	50	f	\N	\N
cffe9701-5de7-4169-afe4-ab8ea9185a11	\N	registration-recaptcha-action	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d27da98e-448f-4020-9702-c880e6bbab18	3	60	f	\N	\N
ca3a5fd3-bf1b-4e11-a3da-e2f22caa8d13	\N	registration-terms-and-conditions	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d27da98e-448f-4020-9702-c880e6bbab18	3	70	f	\N	\N
d89f1c72-73e7-4c39-9d59-7d9ef3d9f1f2	\N	reset-credentials-choose-user	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7880a0dc-0269-4832-bf7e-12e3dc766ffc	0	10	f	\N	\N
4c54c5ec-efa0-48ab-a7d3-0670f97fb661	\N	reset-credential-email	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7880a0dc-0269-4832-bf7e-12e3dc766ffc	0	20	f	\N	\N
36c4a3bd-9343-4941-8ea6-5820ab6df1f1	\N	reset-password	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7880a0dc-0269-4832-bf7e-12e3dc766ffc	0	30	f	\N	\N
498d1b02-8cc2-4841-b382-768a5a404e50	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	7880a0dc-0269-4832-bf7e-12e3dc766ffc	1	40	t	fbb2dfb1-a2e1-49d3-a40d-eae40c793b3b	\N
9465501e-a31d-4bde-b281-b661e7f9a099	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fbb2dfb1-a2e1-49d3-a40d-eae40c793b3b	0	10	f	\N	\N
be69d78d-2f0b-4517-9a40-58865867e702	\N	reset-otp	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fbb2dfb1-a2e1-49d3-a40d-eae40c793b3b	0	20	f	\N	\N
10eaecfb-6d12-4b24-8ac1-99a12a8280fd	\N	client-secret	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fad0f712-db7c-45c6-bab3-266fb23cdfd7	2	10	f	\N	\N
22d5d8c6-b6c2-416c-a34f-c97ef93f8b7d	\N	client-jwt	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fad0f712-db7c-45c6-bab3-266fb23cdfd7	2	20	f	\N	\N
72771393-7063-4796-bb31-b85dcc8e85ec	\N	client-secret-jwt	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fad0f712-db7c-45c6-bab3-266fb23cdfd7	2	30	f	\N	\N
d2680a52-e899-4e31-b098-cd4f85828f1e	\N	client-x509	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	fad0f712-db7c-45c6-bab3-266fb23cdfd7	2	40	f	\N	\N
ea363b7f-456f-493a-8d67-2b6be85b2f84	\N	idp-review-profile	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0fa4a3c3-4a74-4550-a46a-e518cadc1b64	0	10	f	\N	6390b5f5-75d9-4ebb-868c-c497a1194df2
f049e64b-af40-4b4f-9ccd-7e36854c50f4	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0fa4a3c3-4a74-4550-a46a-e518cadc1b64	0	20	t	438412db-7fea-4396-9b26-b28e2904d4d4	\N
d85be229-f152-417f-8596-fd0e84ec4aab	\N	idp-create-user-if-unique	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	438412db-7fea-4396-9b26-b28e2904d4d4	2	10	f	\N	33ed182f-2e31-434b-a3b4-b1064d6d0887
1daae961-6ee0-477b-8bfd-d32d4cd94933	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	438412db-7fea-4396-9b26-b28e2904d4d4	2	20	t	0ce3eb66-d4e2-4ee7-80b7-baa9c24ed829	\N
a0ff3962-2e01-473b-8c5c-6b439f35ccb8	\N	idp-confirm-link	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0ce3eb66-d4e2-4ee7-80b7-baa9c24ed829	0	10	f	\N	\N
0f4a163c-9c89-4fb1-b679-776f9819d043	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0ce3eb66-d4e2-4ee7-80b7-baa9c24ed829	0	20	t	9744d826-f40d-4dd0-a1c0-6482f6ed6d57	\N
bb10eeb6-0f53-413b-94ed-e5907bcb8dec	\N	idp-email-verification	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	9744d826-f40d-4dd0-a1c0-6482f6ed6d57	2	10	f	\N	\N
65d91c28-bd4f-4327-a728-52ae5d138507	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	9744d826-f40d-4dd0-a1c0-6482f6ed6d57	2	20	t	04a9f118-972d-4211-a8bd-8c23d70b7548	\N
516477e5-3b13-4307-a985-7b60952c7039	\N	idp-username-password-form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	04a9f118-972d-4211-a8bd-8c23d70b7548	0	10	f	\N	\N
77e523c7-3099-43cb-8ff0-a3d1075f326b	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	04a9f118-972d-4211-a8bd-8c23d70b7548	1	20	t	ed6613c0-6e6b-44ba-8724-55e81eadf4b6	\N
263cea99-76b4-4457-b9b0-d3e1d2e99e8c	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	ed6613c0-6e6b-44ba-8724-55e81eadf4b6	0	10	f	\N	\N
026bd964-a2a4-4c20-89fd-8d4daae675f0	\N	auth-otp-form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	ed6613c0-6e6b-44ba-8724-55e81eadf4b6	0	20	f	\N	\N
4e9e9bb2-bf27-458d-b3a5-80cff9ea37ab	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0fa4a3c3-4a74-4550-a46a-e518cadc1b64	1	50	t	6eeb8e5b-5c29-4ef7-958a-f74fdc736f5e	\N
65f353a1-ac0f-4476-84b4-450889854244	\N	conditional-user-configured	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	6eeb8e5b-5c29-4ef7-958a-f74fdc736f5e	0	10	f	\N	\N
af0c4f53-2ab8-431e-99fe-ac24a11df8e9	\N	idp-add-organization-member	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	6eeb8e5b-5c29-4ef7-958a-f74fdc736f5e	0	20	f	\N	\N
5906d740-1847-4d8d-a35a-cf256f472f26	\N	http-basic-authenticator	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	4f1c7949-51ff-4d9f-8d63-b7af48e2cd8e	0	10	f	\N	\N
76866485-1e87-4fa0-86fb-092f926e121d	\N	docker-http-basic-authenticator	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	cbc309e1-4dad-4566-939d-3a5965196003	0	10	f	\N	\N
6679f560-6907-40a8-8956-75d3511ef5d6	\N	auth-cookie	83e82497-9a85-4600-9b3d-877c38c4ff59	de9d9e28-6996-4778-ab16-16090e34971b	2	10	f	\N	\N
e6bd831d-d0be-4370-88c3-5c7a5498506d	\N	auth-spnego	83e82497-9a85-4600-9b3d-877c38c4ff59	de9d9e28-6996-4778-ab16-16090e34971b	3	20	f	\N	\N
da483130-6a5d-4652-b0ce-d2fe3356b492	\N	identity-provider-redirector	83e82497-9a85-4600-9b3d-877c38c4ff59	de9d9e28-6996-4778-ab16-16090e34971b	2	25	f	\N	\N
7d417ae9-90ed-4f62-a898-f81e835392be	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	de9d9e28-6996-4778-ab16-16090e34971b	2	30	t	6bbe1811-b37d-4ed4-a35e-a621d2c31a1e	\N
f878ad67-dd3b-4d99-a8c3-a6ec2303099d	\N	auth-username-password-form	83e82497-9a85-4600-9b3d-877c38c4ff59	6bbe1811-b37d-4ed4-a35e-a621d2c31a1e	0	10	f	\N	\N
69d508a5-7cbd-478e-9867-a3be10fc8a80	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	6bbe1811-b37d-4ed4-a35e-a621d2c31a1e	1	20	t	caff4950-952d-454d-9c3a-92e0424f4ff1	\N
e4a9e66a-b27d-4dc9-93dd-667c3e5c315a	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	caff4950-952d-454d-9c3a-92e0424f4ff1	0	10	f	\N	\N
c8d7d08e-ffea-4637-a29d-189e68ef625e	\N	auth-otp-form	83e82497-9a85-4600-9b3d-877c38c4ff59	caff4950-952d-454d-9c3a-92e0424f4ff1	0	20	f	\N	\N
a29ebcaa-d30d-4b58-a870-48c3357e7fca	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	de9d9e28-6996-4778-ab16-16090e34971b	2	26	t	21282c6c-2c14-4fd8-8532-17be84845267	\N
9faff544-e1c2-4f58-a941-14aa6907c0a8	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	21282c6c-2c14-4fd8-8532-17be84845267	1	10	t	f086a423-14fa-40c2-ad3c-e7b1b7aab892	\N
52eada44-f994-439b-add3-f8762e3a58c1	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	f086a423-14fa-40c2-ad3c-e7b1b7aab892	0	10	f	\N	\N
806ceac9-d595-4621-83a0-e615617b639d	\N	organization	83e82497-9a85-4600-9b3d-877c38c4ff59	f086a423-14fa-40c2-ad3c-e7b1b7aab892	2	20	f	\N	\N
4be916a7-e03d-450e-997a-29c29a01add2	\N	direct-grant-validate-username	83e82497-9a85-4600-9b3d-877c38c4ff59	74ea7d61-4e60-4d7c-bbe2-2d7d141f2c0f	0	10	f	\N	\N
e4124050-670c-4d51-857a-0b1feb4eb79f	\N	direct-grant-validate-password	83e82497-9a85-4600-9b3d-877c38c4ff59	74ea7d61-4e60-4d7c-bbe2-2d7d141f2c0f	0	20	f	\N	\N
263d8f13-0b81-495f-8a99-4f3a694cf871	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	74ea7d61-4e60-4d7c-bbe2-2d7d141f2c0f	1	30	t	abef78ff-ad35-4994-94fb-b1adbc15c81c	\N
be30d6e8-3900-4b7a-a021-ba9925b1a9b2	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	abef78ff-ad35-4994-94fb-b1adbc15c81c	0	10	f	\N	\N
8d2803b0-529a-4851-8106-e34fae869649	\N	direct-grant-validate-otp	83e82497-9a85-4600-9b3d-877c38c4ff59	abef78ff-ad35-4994-94fb-b1adbc15c81c	0	20	f	\N	\N
348ecd1f-a222-470f-84fe-3c879a754812	\N	registration-page-form	83e82497-9a85-4600-9b3d-877c38c4ff59	828a6cd0-d3ce-4aac-9db0-b3f4f8503425	0	10	t	ea0234c4-cabd-4403-9df2-8296843114c6	\N
dc3ab99f-aa95-44ef-bd24-d14db506e0cd	\N	registration-user-creation	83e82497-9a85-4600-9b3d-877c38c4ff59	ea0234c4-cabd-4403-9df2-8296843114c6	0	20	f	\N	\N
232605e5-47ff-4303-a76d-c9d3b899dc18	\N	registration-password-action	83e82497-9a85-4600-9b3d-877c38c4ff59	ea0234c4-cabd-4403-9df2-8296843114c6	0	50	f	\N	\N
0dedb5d9-1925-4b75-a2f4-1dbc208d1af3	\N	registration-recaptcha-action	83e82497-9a85-4600-9b3d-877c38c4ff59	ea0234c4-cabd-4403-9df2-8296843114c6	3	60	f	\N	\N
e036fdd9-7e97-40b2-8e8c-ff8dbc4621d3	\N	registration-terms-and-conditions	83e82497-9a85-4600-9b3d-877c38c4ff59	ea0234c4-cabd-4403-9df2-8296843114c6	3	70	f	\N	\N
3cf1a1c4-d088-4597-b9a6-0b583554b75b	\N	reset-credentials-choose-user	83e82497-9a85-4600-9b3d-877c38c4ff59	9b5cc723-5e89-4420-a861-9215405ca68e	0	10	f	\N	\N
eaaba78a-d4f2-4a15-81c9-b6ad01565dc3	\N	reset-credential-email	83e82497-9a85-4600-9b3d-877c38c4ff59	9b5cc723-5e89-4420-a861-9215405ca68e	0	20	f	\N	\N
1ad7ba34-cc52-4a28-a5af-513b49ba0cfc	\N	reset-password	83e82497-9a85-4600-9b3d-877c38c4ff59	9b5cc723-5e89-4420-a861-9215405ca68e	0	30	f	\N	\N
b98a93a4-39d0-43a9-b9ce-196b03aa289d	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	9b5cc723-5e89-4420-a861-9215405ca68e	1	40	t	a182d7ba-a6df-4658-bed5-c5ac927847d1	\N
3920a06d-050e-4ab0-93d5-2a96f596d3e8	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	a182d7ba-a6df-4658-bed5-c5ac927847d1	0	10	f	\N	\N
c7518579-69b7-4369-8f09-4b34272d4f5e	\N	reset-otp	83e82497-9a85-4600-9b3d-877c38c4ff59	a182d7ba-a6df-4658-bed5-c5ac927847d1	0	20	f	\N	\N
d39845ea-12fc-431e-b20d-69a538884028	\N	client-secret	83e82497-9a85-4600-9b3d-877c38c4ff59	29c3a662-3824-4f5a-a9f0-5af5be80698f	2	10	f	\N	\N
ab536ae8-be49-46f8-854f-2ec20ab0df3e	\N	client-jwt	83e82497-9a85-4600-9b3d-877c38c4ff59	29c3a662-3824-4f5a-a9f0-5af5be80698f	2	20	f	\N	\N
f4344286-5d8d-40c9-be63-7fcca4e7f549	\N	client-secret-jwt	83e82497-9a85-4600-9b3d-877c38c4ff59	29c3a662-3824-4f5a-a9f0-5af5be80698f	2	30	f	\N	\N
11ec8eba-4457-4730-9cf6-03687b068190	\N	client-x509	83e82497-9a85-4600-9b3d-877c38c4ff59	29c3a662-3824-4f5a-a9f0-5af5be80698f	2	40	f	\N	\N
bd4e6652-b37f-41d0-b989-a258f22f4166	\N	idp-review-profile	83e82497-9a85-4600-9b3d-877c38c4ff59	68469895-a771-4df2-881c-ccad0a7e8081	0	10	f	\N	5350baf8-49b0-4812-8d46-cce49f223d22
12869ca8-a446-46f4-84e8-70124878a513	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	68469895-a771-4df2-881c-ccad0a7e8081	0	20	t	3e918ee3-f9f6-40a2-b1e9-08816414d496	\N
f5f9ed53-eaa1-4471-bba0-9662d7cd3387	\N	idp-create-user-if-unique	83e82497-9a85-4600-9b3d-877c38c4ff59	3e918ee3-f9f6-40a2-b1e9-08816414d496	2	10	f	\N	beacc915-2005-4fa4-a5ce-384384516d21
0ef69650-7c57-4cb4-9a27-6a56fa4a7283	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	3e918ee3-f9f6-40a2-b1e9-08816414d496	2	20	t	d8aff831-c066-40e5-a951-30448f1a76d8	\N
dab541d7-7baf-45b2-be8f-fedf78773e7c	\N	idp-confirm-link	83e82497-9a85-4600-9b3d-877c38c4ff59	d8aff831-c066-40e5-a951-30448f1a76d8	0	10	f	\N	\N
8a6fd6cd-1050-4cc3-b7f2-b3969a165f8a	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	d8aff831-c066-40e5-a951-30448f1a76d8	0	20	t	67e6bcf8-0c0a-4d50-ab41-26d86dc47ff1	\N
3f102baf-6a49-4980-92be-6d03c91ca070	\N	idp-email-verification	83e82497-9a85-4600-9b3d-877c38c4ff59	67e6bcf8-0c0a-4d50-ab41-26d86dc47ff1	2	10	f	\N	\N
ae6ad3ab-d5e7-4fcc-a21f-c6c3fc2026e1	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	67e6bcf8-0c0a-4d50-ab41-26d86dc47ff1	2	20	t	c2498e5b-3ca6-4546-a8fd-842e6d212cd8	\N
0a2b74de-886b-40ed-8c57-ea458e826ea1	\N	idp-username-password-form	83e82497-9a85-4600-9b3d-877c38c4ff59	c2498e5b-3ca6-4546-a8fd-842e6d212cd8	0	10	f	\N	\N
3df969b6-121e-48b3-8f81-7e3032337f9e	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	c2498e5b-3ca6-4546-a8fd-842e6d212cd8	1	20	t	ef8e71a8-49ce-417f-82af-a3942294a9d3	\N
8c416f0a-a7ca-4ffb-bf61-05639389df5b	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	ef8e71a8-49ce-417f-82af-a3942294a9d3	0	10	f	\N	\N
55cd66ec-fa46-44c7-96d2-d75f4c94fb6b	\N	auth-otp-form	83e82497-9a85-4600-9b3d-877c38c4ff59	ef8e71a8-49ce-417f-82af-a3942294a9d3	0	20	f	\N	\N
6d3176f2-a590-49e1-9eda-1c085c2fa444	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	68469895-a771-4df2-881c-ccad0a7e8081	1	50	t	2b1d47c7-2ec3-44c0-a6d4-964f488ca91f	\N
545024c5-b9be-48e9-9ade-9d273d31e8cd	\N	conditional-user-configured	83e82497-9a85-4600-9b3d-877c38c4ff59	2b1d47c7-2ec3-44c0-a6d4-964f488ca91f	0	10	f	\N	\N
2561e360-75d2-4d6e-9a7d-139fec272338	\N	idp-add-organization-member	83e82497-9a85-4600-9b3d-877c38c4ff59	2b1d47c7-2ec3-44c0-a6d4-964f488ca91f	0	20	f	\N	\N
e7635fa9-438e-4e46-a2df-62f9ee32230e	\N	http-basic-authenticator	83e82497-9a85-4600-9b3d-877c38c4ff59	5728d50a-987a-4932-b850-fa0e26c54f6a	0	10	f	\N	\N
4f3fe2ea-9edd-47ff-b979-9920c91edbd7	\N	docker-http-basic-authenticator	83e82497-9a85-4600-9b3d-877c38c4ff59	f4ce9663-c40a-4556-8483-bda50deee9ba	0	10	f	\N	\N
\.


--
-- Data for Name: authentication_flow; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.authentication_flow (id, alias, description, realm_id, provider_id, top_level, built_in) FROM stdin;
8d932c23-9b14-4631-9115-68fb5c1280e6	browser	Browser based authentication	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
d9e7f8c7-7698-41fc-8942-3d2f2a8bc524	forms	Username, password, otp and other auth forms.	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
301872f9-c562-4b04-809c-1d3060737911	Browser - Conditional OTP	Flow to determine if the OTP is required for the authentication	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
8ea741aa-a19b-4f69-85f2-c44caced0001	direct grant	OpenID Connect Resource Owner Grant	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
29ff59dc-fe15-4b7e-9e6e-8ef80ef7ce04	Direct Grant - Conditional OTP	Flow to determine if the OTP is required for the authentication	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
3d047957-c675-4bd6-b8b8-ba1388bfe7bc	registration	Registration flow	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
13e4dd1f-5d2f-4451-bbac-b2830c29dff1	registration form	Registration form	259bfe83-5374-4ae7-bb38-5464d0d5bc75	form-flow	f	t
e21e4d5f-321d-48a6-96b6-91e5c4b140b3	reset credentials	Reset credentials for a user if they forgot their password or something	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
e3a43a6c-8d01-4d5d-8309-1e78a8333656	Reset - Conditional OTP	Flow to determine if the OTP should be reset or not. Set to REQUIRED to force.	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
2a7e05b5-60dc-4103-92af-1cbca75f5a5f	clients	Base authentication for clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	client-flow	t	t
e89f2d94-88af-4e46-9ebd-42ff5d0bed91	first broker login	Actions taken after first broker login with identity provider account, which is not yet linked to any Keycloak account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
835111b5-68bd-45e6-91d9-9fb2d899d16e	User creation or linking	Flow for the existing/non-existing user alternatives	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
26cf07ec-50b6-4940-a4a5-a13cf322142d	Handle Existing Account	Handle what to do if there is existing account with same email/username like authenticated identity provider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
01c28904-fc66-4c5e-b4aa-7de5ed9f712b	Account verification options	Method with which to verity the existing account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
c06eb2d4-a931-4693-b328-6a0c2712a262	Verify Existing Account by Re-authentication	Reauthentication of existing account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
f233d1fc-f469-43ee-a545-ddd28769df18	First broker login - Conditional OTP	Flow to determine if the OTP is required for the authentication	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	f	t
e3925878-9c24-443d-bc2e-39ae8c9fa370	saml ecp	SAML ECP Profile Authentication Flow	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
747a49b9-eea1-4846-b2f5-3a7543b6cb5b	docker auth	Used by Docker clients to authenticate against the IDP	259bfe83-5374-4ae7-bb38-5464d0d5bc75	basic-flow	t	t
f28f4ca9-ac34-4863-8736-baa41db4d706	browser	Browser based authentication	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
561d2206-7df3-4b3e-83c2-1963abf9cc2f	forms	Username, password, otp and other auth forms.	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
926f38ee-ac84-431c-84b4-b278f1f672da	Browser - Conditional OTP	Flow to determine if the OTP is required for the authentication	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
094a6276-4ed6-410a-83ed-72a94b565e0c	Organization	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
7131551b-c146-4a72-a415-9f3a0543cbf6	Browser - Conditional Organization	Flow to determine if the organization identity-first login is to be used	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
c385709a-f0bc-405e-8b86-6502e1bd1709	direct grant	OpenID Connect Resource Owner Grant	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
8d620c92-0651-4971-9bf6-2bc3129e272d	Direct Grant - Conditional OTP	Flow to determine if the OTP is required for the authentication	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
7ef380b4-c2ce-4684-ac9c-66fcedec973c	registration	Registration flow	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
d27da98e-448f-4020-9702-c880e6bbab18	registration form	Registration form	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	form-flow	f	t
7880a0dc-0269-4832-bf7e-12e3dc766ffc	reset credentials	Reset credentials for a user if they forgot their password or something	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
fbb2dfb1-a2e1-49d3-a40d-eae40c793b3b	Reset - Conditional OTP	Flow to determine if the OTP should be reset or not. Set to REQUIRED to force.	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
fad0f712-db7c-45c6-bab3-266fb23cdfd7	clients	Base authentication for clients	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	client-flow	t	t
0fa4a3c3-4a74-4550-a46a-e518cadc1b64	first broker login	Actions taken after first broker login with identity provider account, which is not yet linked to any Keycloak account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
438412db-7fea-4396-9b26-b28e2904d4d4	User creation or linking	Flow for the existing/non-existing user alternatives	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
0ce3eb66-d4e2-4ee7-80b7-baa9c24ed829	Handle Existing Account	Handle what to do if there is existing account with same email/username like authenticated identity provider	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
9744d826-f40d-4dd0-a1c0-6482f6ed6d57	Account verification options	Method with which to verity the existing account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
04a9f118-972d-4211-a8bd-8c23d70b7548	Verify Existing Account by Re-authentication	Reauthentication of existing account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
ed6613c0-6e6b-44ba-8724-55e81eadf4b6	First broker login - Conditional OTP	Flow to determine if the OTP is required for the authentication	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
6eeb8e5b-5c29-4ef7-958a-f74fdc736f5e	First Broker Login - Conditional Organization	Flow to determine if the authenticator that adds organization members is to be used	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	f	t
4f1c7949-51ff-4d9f-8d63-b7af48e2cd8e	saml ecp	SAML ECP Profile Authentication Flow	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
cbc309e1-4dad-4566-939d-3a5965196003	docker auth	Used by Docker clients to authenticate against the IDP	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	basic-flow	t	t
de9d9e28-6996-4778-ab16-16090e34971b	browser	Browser based authentication	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
6bbe1811-b37d-4ed4-a35e-a621d2c31a1e	forms	Username, password, otp and other auth forms.	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
caff4950-952d-454d-9c3a-92e0424f4ff1	Browser - Conditional OTP	Flow to determine if the OTP is required for the authentication	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
21282c6c-2c14-4fd8-8532-17be84845267	Organization	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
f086a423-14fa-40c2-ad3c-e7b1b7aab892	Browser - Conditional Organization	Flow to determine if the organization identity-first login is to be used	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
74ea7d61-4e60-4d7c-bbe2-2d7d141f2c0f	direct grant	OpenID Connect Resource Owner Grant	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
abef78ff-ad35-4994-94fb-b1adbc15c81c	Direct Grant - Conditional OTP	Flow to determine if the OTP is required for the authentication	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
828a6cd0-d3ce-4aac-9db0-b3f4f8503425	registration	Registration flow	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
ea0234c4-cabd-4403-9df2-8296843114c6	registration form	Registration form	83e82497-9a85-4600-9b3d-877c38c4ff59	form-flow	f	t
9b5cc723-5e89-4420-a861-9215405ca68e	reset credentials	Reset credentials for a user if they forgot their password or something	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
a182d7ba-a6df-4658-bed5-c5ac927847d1	Reset - Conditional OTP	Flow to determine if the OTP should be reset or not. Set to REQUIRED to force.	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
29c3a662-3824-4f5a-a9f0-5af5be80698f	clients	Base authentication for clients	83e82497-9a85-4600-9b3d-877c38c4ff59	client-flow	t	t
68469895-a771-4df2-881c-ccad0a7e8081	first broker login	Actions taken after first broker login with identity provider account, which is not yet linked to any Keycloak account	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
3e918ee3-f9f6-40a2-b1e9-08816414d496	User creation or linking	Flow for the existing/non-existing user alternatives	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
d8aff831-c066-40e5-a951-30448f1a76d8	Handle Existing Account	Handle what to do if there is existing account with same email/username like authenticated identity provider	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
67e6bcf8-0c0a-4d50-ab41-26d86dc47ff1	Account verification options	Method with which to verity the existing account	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
c2498e5b-3ca6-4546-a8fd-842e6d212cd8	Verify Existing Account by Re-authentication	Reauthentication of existing account	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
ef8e71a8-49ce-417f-82af-a3942294a9d3	First broker login - Conditional OTP	Flow to determine if the OTP is required for the authentication	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
2b1d47c7-2ec3-44c0-a6d4-964f488ca91f	First Broker Login - Conditional Organization	Flow to determine if the authenticator that adds organization members is to be used	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	f	t
5728d50a-987a-4932-b850-fa0e26c54f6a	saml ecp	SAML ECP Profile Authentication Flow	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
f4ce9663-c40a-4556-8483-bda50deee9ba	docker auth	Used by Docker clients to authenticate against the IDP	83e82497-9a85-4600-9b3d-877c38c4ff59	basic-flow	t	t
\.


--
-- Data for Name: authenticator_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.authenticator_config (id, alias, realm_id) FROM stdin;
b6f57804-c949-4e40-afad-92bdeb8c6e2d	review profile config	259bfe83-5374-4ae7-bb38-5464d0d5bc75
346c4279-8968-4841-b8ac-5c461d6493ea	create unique user config	259bfe83-5374-4ae7-bb38-5464d0d5bc75
6390b5f5-75d9-4ebb-868c-c497a1194df2	review profile config	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26
33ed182f-2e31-434b-a3b4-b1064d6d0887	create unique user config	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26
5350baf8-49b0-4812-8d46-cce49f223d22	review profile config	83e82497-9a85-4600-9b3d-877c38c4ff59
beacc915-2005-4fa4-a5ce-384384516d21	create unique user config	83e82497-9a85-4600-9b3d-877c38c4ff59
\.


--
-- Data for Name: authenticator_config_entry; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.authenticator_config_entry (authenticator_id, value, name) FROM stdin;
346c4279-8968-4841-b8ac-5c461d6493ea	false	require.password.update.after.registration
b6f57804-c949-4e40-afad-92bdeb8c6e2d	missing	update.profile.on.first.login
33ed182f-2e31-434b-a3b4-b1064d6d0887	false	require.password.update.after.registration
6390b5f5-75d9-4ebb-868c-c497a1194df2	missing	update.profile.on.first.login
5350baf8-49b0-4812-8d46-cce49f223d22	missing	update.profile.on.first.login
beacc915-2005-4fa4-a5ce-384384516d21	false	require.password.update.after.registration
\.


--
-- Data for Name: broker_link; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.broker_link (identity_provider, storage_provider_id, realm_id, broker_user_id, broker_username, token, user_id) FROM stdin;
\.


--
-- Data for Name: client; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client (id, enabled, full_scope_allowed, client_id, not_before, public_client, secret, base_url, bearer_only, management_url, surrogate_auth_required, realm_id, protocol, node_rereg_timeout, frontchannel_logout, consent_required, name, service_accounts_enabled, client_authenticator_type, root_url, description, registration_token, standard_flow_enabled, implicit_flow_enabled, direct_access_grants_enabled, always_display_in_console) FROM stdin;
21023f50-efbf-4680-94a0-dd36232f70b2	t	f	master-realm	0	f	\N	\N	t	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	0	f	f	master Realm	f	client-secret	\N	\N	\N	t	f	f	f
3d56e973-75fb-4d57-bb78-302af297fe9d	t	f	account	0	t	\N	/realms/master/account/	f	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	0	f	f	${client_account}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
1468f635-7aee-4d08-9633-86593942e889	t	f	account-console	0	t	\N	/realms/master/account/	f	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	0	f	f	${client_account-console}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	t	f	broker	0	f	\N	\N	t	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	0	f	f	${client_broker}	f	client-secret	\N	\N	\N	t	f	f	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	t	t	security-admin-console	0	t	\N	/admin/master/console/	f	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	0	f	f	${client_security-admin-console}	f	client-secret	${authAdminUrl}	\N	\N	t	f	f	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	t	t	admin-cli	0	t	\N	\N	f	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	0	f	f	${client_admin-cli}	f	client-secret	\N	\N	\N	f	f	t	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	t	t	backend	0	f	GzaqeEjEzyFdC8myzVL4y3aLdLLCBBDC		f		f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	openid-connect	-1	t	f	backend	t	client-secret			\N	t	f	t	f
741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	f	platforme_school-realm	0	f	\N	\N	t	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	0	f	f	platforme_school Realm	f	client-secret	\N	\N	\N	t	f	f	f
950ca38d-f174-4392-adef-9153ec984d2b	t	f	realm-management	0	f	\N	\N	t	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_realm-management}	f	client-secret	\N	\N	\N	t	f	f	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	f	account	0	t	\N	/realms/platforme_school/account/	f	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_account}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
d2dfba85-0770-401f-9388-86bb979ab264	t	f	account-console	0	t	\N	/realms/platforme_school/account/	f	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_account-console}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	t	f	broker	0	f	\N	\N	t	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_broker}	f	client-secret	\N	\N	\N	t	f	f	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	t	t	security-admin-console	0	t	\N	/admin/platforme_school/console/	f	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_security-admin-console}	f	client-secret	${authAdminUrl}	\N	\N	t	f	f	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	t	t	admin-cli	0	t	\N	\N	f	\N	f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	0	f	f	${client_admin-cli}	f	client-secret	\N	\N	\N	f	f	t	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	t	f	account-console	0	t	\N	/realms/smartschool/account/	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_account-console}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	t	t	smartschool-backend	0	f	trqpC1hwEJAV0GK71WdldP9BdXBXDPAU		f		f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	-1	t	f		t	client-secret			\N	t	f	t	f
81b24093-5b2e-4796-9adc-970cd6e97d98	t	t	smartschool-frontend	0	t	\N		f		f	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	openid-connect	-1	t	f		f	client-secret			\N	t	f	t	f
9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	f	smartschool-realm	0	f	\N	\N	t	\N	f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	0	f	f	smartschool Realm	f	client-secret	\N	\N	\N	t	f	f	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	t	f	realm-management	0	f	\N	\N	t	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_realm-management}	f	client-secret	\N	\N	\N	t	f	f	f
4a7c7844-cde9-4654-b214-b2bd003f2344	t	f	account	0	t	\N	/realms/smartschool/account/	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_account}	f	client-secret	${authBaseUrl}	\N	\N	t	f	f	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	t	f	broker	0	f	\N	\N	t	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_broker}	f	client-secret	\N	\N	\N	t	f	f	f
d5442a0f-3191-407b-befa-391aaa28773d	t	t	security-admin-console	0	t	\N	/admin/smartschool/console/	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_security-admin-console}	f	client-secret	${authAdminUrl}	\N	\N	t	f	f	f
e932343c-9370-4ae5-878d-a4f008065947	t	t	admin-cli	0	t	\N	\N	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	0	f	f	${client_admin-cli}	f	client-secret	\N	\N	\N	f	f	t	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	t	t	smartschool-frontend	0	t	\N	\N	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	-1	f	f	SmartSchool Frontend	f	client-secret	\N	\N	\N	t	f	t	f
5907c420-e9c0-4884-85ed-a753a9be228e	t	t	smartschool-backend	0	f	RQSLRdMsBUWvHio5vPREGZWZRteNlO40	\N	f	\N	f	83e82497-9a85-4600-9b3d-877c38c4ff59	openid-connect	-1	f	f	SmartSchool Backend	t	client-secret	\N	\N	\N	f	f	f	f
\.


--
-- Data for Name: client_attributes; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_attributes (client_id, name, value) FROM stdin;
3d56e973-75fb-4d57-bb78-302af297fe9d	post.logout.redirect.uris	+
1468f635-7aee-4d08-9633-86593942e889	post.logout.redirect.uris	+
1468f635-7aee-4d08-9633-86593942e889	pkce.code.challenge.method	S256
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	post.logout.redirect.uris	+
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	pkce.code.challenge.method	S256
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	client.use.lightweight.access.token.enabled	true
6909eac5-21f8-47b5-b033-0915b4c3bbcf	client.use.lightweight.access.token.enabled	true
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	client.secret.creation.time	1782732484
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	oauth2.device.authorization.grant.enabled	false
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	oidc.ciba.grant.enabled	false
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	backchannel.logout.session.required	true
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	backchannel.logout.revoke.offline.tokens	false
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	realm_client	false
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	display.on.consent.screen	false
d022332e-3f8b-44dc-9e5c-c6751920ab3d	post.logout.redirect.uris	+
d2dfba85-0770-401f-9388-86bb979ab264	post.logout.redirect.uris	+
d2dfba85-0770-401f-9388-86bb979ab264	pkce.code.challenge.method	S256
cbcbcbde-a715-4358-a3c3-7f027c4436b6	post.logout.redirect.uris	+
cbcbcbde-a715-4358-a3c3-7f027c4436b6	pkce.code.challenge.method	S256
cbcbcbde-a715-4358-a3c3-7f027c4436b6	client.use.lightweight.access.token.enabled	true
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	client.use.lightweight.access.token.enabled	true
2d441a2b-1316-4adc-b51a-8e2520fc33e6	client.secret.creation.time	1782817377
2d441a2b-1316-4adc-b51a-8e2520fc33e6	oauth2.device.authorization.grant.enabled	false
2d441a2b-1316-4adc-b51a-8e2520fc33e6	oidc.ciba.grant.enabled	false
2d441a2b-1316-4adc-b51a-8e2520fc33e6	backchannel.logout.session.required	true
2d441a2b-1316-4adc-b51a-8e2520fc33e6	backchannel.logout.revoke.offline.tokens	false
81b24093-5b2e-4796-9adc-970cd6e97d98	oauth2.device.authorization.grant.enabled	false
81b24093-5b2e-4796-9adc-970cd6e97d98	oidc.ciba.grant.enabled	false
81b24093-5b2e-4796-9adc-970cd6e97d98	backchannel.logout.session.required	true
81b24093-5b2e-4796-9adc-970cd6e97d98	backchannel.logout.revoke.offline.tokens	false
81b24093-5b2e-4796-9adc-970cd6e97d98	realm_client	false
81b24093-5b2e-4796-9adc-970cd6e97d98	display.on.consent.screen	false
4a7c7844-cde9-4654-b214-b2bd003f2344	post.logout.redirect.uris	+
cba0a035-784d-4ec0-9c98-64f8bb89002b	post.logout.redirect.uris	+
cba0a035-784d-4ec0-9c98-64f8bb89002b	pkce.code.challenge.method	S256
d5442a0f-3191-407b-befa-391aaa28773d	post.logout.redirect.uris	+
d5442a0f-3191-407b-befa-391aaa28773d	pkce.code.challenge.method	S256
d5442a0f-3191-407b-befa-391aaa28773d	client.use.lightweight.access.token.enabled	true
e932343c-9370-4ae5-878d-a4f008065947	client.use.lightweight.access.token.enabled	true
2e5dede9-f6b2-489c-8c2b-44ca02f30878	backchannel.logout.session.required	true
2e5dede9-f6b2-489c-8c2b-44ca02f30878	backchannel.logout.revoke.offline.tokens	false
5907c420-e9c0-4884-85ed-a753a9be228e	client.secret.creation.time	1782818905
5907c420-e9c0-4884-85ed-a753a9be228e	backchannel.logout.session.required	true
5907c420-e9c0-4884-85ed-a753a9be228e	backchannel.logout.revoke.offline.tokens	false
\.


--
-- Data for Name: client_auth_flow_bindings; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_auth_flow_bindings (client_id, flow_id, binding_name) FROM stdin;
\.


--
-- Data for Name: client_initial_access; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_initial_access (id, realm_id, "timestamp", expiration, count, remaining_count) FROM stdin;
\.


--
-- Data for Name: client_node_registrations; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_node_registrations (client_id, value, name) FROM stdin;
\.


--
-- Data for Name: client_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_scope (id, name, realm_id, description, protocol) FROM stdin;
17c8a13e-ea53-420e-b0cd-1b251905e541	offline_access	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect built-in scope: offline_access	openid-connect
ca67e32d-e6cb-47d1-b92b-e6134d930814	role_list	259bfe83-5374-4ae7-bb38-5464d0d5bc75	SAML role list	saml
c595b026-4f21-49a3-97c3-5dd334ad50e8	saml_organization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	Organization Membership	saml
a5930a85-5391-46e7-946b-887c57753f84	profile	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect built-in scope: profile	openid-connect
50ea777d-bde5-4b4b-a26d-ba584c7fea16	email	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect built-in scope: email	openid-connect
ad2ee486-c593-4dbd-b090-74d53c2c21f5	address	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect built-in scope: address	openid-connect
466ce05b-3dc9-4495-af1e-fd319ae830bb	phone	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect built-in scope: phone	openid-connect
bc484b1a-c523-4e3f-a799-1c8ecfec14f1	roles	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect scope for add user roles to the access token	openid-connect
52461eff-b39c-404b-9be3-1b2bee4b5af3	web-origins	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect scope for add allowed web origins to the access token	openid-connect
5dec384e-eccb-4f03-8078-81b35b6319d6	microprofile-jwt	259bfe83-5374-4ae7-bb38-5464d0d5bc75	Microprofile - JWT built-in scope	openid-connect
92c21645-ea9c-4433-abfd-c3323c4d7fc0	acr	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect scope for add acr (authentication context class reference) to the token	openid-connect
9282382a-6d8c-4529-b174-cb7b87276a67	basic	259bfe83-5374-4ae7-bb38-5464d0d5bc75	OpenID Connect scope for add all basic claims to the token	openid-connect
b5d3388f-c9b1-4bf6-aef0-b942f0015427	organization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	Additional claims about the organization a subject belongs to	openid-connect
e8b8adf3-2e48-406e-b03c-ee798b047ba6	offline_access	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect built-in scope: offline_access	openid-connect
2fd5f4d6-e0d0-434b-b647-b441196a86af	role_list	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	SAML role list	saml
ca7f8f17-b3a2-4577-a6df-781b426a4052	saml_organization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	Organization Membership	saml
c86b7616-505a-47db-acef-beb669c1a5ec	profile	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect built-in scope: profile	openid-connect
89105071-a02d-448c-baaf-1197e4bb223d	email	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect built-in scope: email	openid-connect
61d5c7ba-888d-4678-9834-ece659a1dc75	address	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect built-in scope: address	openid-connect
195f10d2-6623-47d2-855e-7db6981c182d	phone	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect built-in scope: phone	openid-connect
c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	roles	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect scope for add user roles to the access token	openid-connect
a9f69da8-e105-499f-a44e-22ecd6043f95	web-origins	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect scope for add allowed web origins to the access token	openid-connect
5237f92a-3544-409d-a97e-551d5c8f27d7	microprofile-jwt	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	Microprofile - JWT built-in scope	openid-connect
c579e87e-c2cd-443c-b5bc-f6e35dd4b259	acr	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect scope for add acr (authentication context class reference) to the token	openid-connect
f044e4ae-6203-4844-a78c-84fb60b0cd72	basic	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	OpenID Connect scope for add all basic claims to the token	openid-connect
0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	organization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	Additional claims about the organization a subject belongs to	openid-connect
b8a17460-770a-4aeb-aba3-c8e453f242f8	offline_access	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect built-in scope: offline_access	openid-connect
6c136e2a-5769-4566-9c62-568b1bf54a20	role_list	83e82497-9a85-4600-9b3d-877c38c4ff59	SAML role list	saml
d28411e0-ff79-4ab9-8c96-302b3ae89996	saml_organization	83e82497-9a85-4600-9b3d-877c38c4ff59	Organization Membership	saml
9bf8f3c7-2599-44d8-93c8-375927f6a67a	profile	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect built-in scope: profile	openid-connect
604c75b6-95dd-45d1-af05-eb4c7ec83dc3	email	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect built-in scope: email	openid-connect
d9af3ac6-a7da-484a-97ce-5e1e25badc5f	address	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect built-in scope: address	openid-connect
38f5bf2d-dad0-449f-8d0c-438a4edb1540	phone	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect built-in scope: phone	openid-connect
9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	roles	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect scope for add user roles to the access token	openid-connect
0d3d0b4f-8f6d-4b45-8914-b383d61ca069	web-origins	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect scope for add allowed web origins to the access token	openid-connect
735ea178-7454-4888-b330-21b6c6c200f9	microprofile-jwt	83e82497-9a85-4600-9b3d-877c38c4ff59	Microprofile - JWT built-in scope	openid-connect
29c4f76f-4ac1-42f3-8876-cd82b644632e	acr	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect scope for add acr (authentication context class reference) to the token	openid-connect
b8081550-653f-403f-8e2c-f5059baf4c44	basic	83e82497-9a85-4600-9b3d-877c38c4ff59	OpenID Connect scope for add all basic claims to the token	openid-connect
b290d0cc-d348-4360-b168-8ac976c0085a	organization	83e82497-9a85-4600-9b3d-877c38c4ff59	Additional claims about the organization a subject belongs to	openid-connect
\.


--
-- Data for Name: client_scope_attributes; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_scope_attributes (scope_id, value, name) FROM stdin;
17c8a13e-ea53-420e-b0cd-1b251905e541	true	display.on.consent.screen
17c8a13e-ea53-420e-b0cd-1b251905e541	${offlineAccessScopeConsentText}	consent.screen.text
ca67e32d-e6cb-47d1-b92b-e6134d930814	true	display.on.consent.screen
ca67e32d-e6cb-47d1-b92b-e6134d930814	${samlRoleListScopeConsentText}	consent.screen.text
c595b026-4f21-49a3-97c3-5dd334ad50e8	false	display.on.consent.screen
a5930a85-5391-46e7-946b-887c57753f84	true	display.on.consent.screen
a5930a85-5391-46e7-946b-887c57753f84	${profileScopeConsentText}	consent.screen.text
a5930a85-5391-46e7-946b-887c57753f84	true	include.in.token.scope
50ea777d-bde5-4b4b-a26d-ba584c7fea16	true	display.on.consent.screen
50ea777d-bde5-4b4b-a26d-ba584c7fea16	${emailScopeConsentText}	consent.screen.text
50ea777d-bde5-4b4b-a26d-ba584c7fea16	true	include.in.token.scope
ad2ee486-c593-4dbd-b090-74d53c2c21f5	true	display.on.consent.screen
ad2ee486-c593-4dbd-b090-74d53c2c21f5	${addressScopeConsentText}	consent.screen.text
ad2ee486-c593-4dbd-b090-74d53c2c21f5	true	include.in.token.scope
466ce05b-3dc9-4495-af1e-fd319ae830bb	true	display.on.consent.screen
466ce05b-3dc9-4495-af1e-fd319ae830bb	${phoneScopeConsentText}	consent.screen.text
466ce05b-3dc9-4495-af1e-fd319ae830bb	true	include.in.token.scope
bc484b1a-c523-4e3f-a799-1c8ecfec14f1	true	display.on.consent.screen
bc484b1a-c523-4e3f-a799-1c8ecfec14f1	${rolesScopeConsentText}	consent.screen.text
bc484b1a-c523-4e3f-a799-1c8ecfec14f1	false	include.in.token.scope
52461eff-b39c-404b-9be3-1b2bee4b5af3	false	display.on.consent.screen
52461eff-b39c-404b-9be3-1b2bee4b5af3		consent.screen.text
52461eff-b39c-404b-9be3-1b2bee4b5af3	false	include.in.token.scope
5dec384e-eccb-4f03-8078-81b35b6319d6	false	display.on.consent.screen
5dec384e-eccb-4f03-8078-81b35b6319d6	true	include.in.token.scope
92c21645-ea9c-4433-abfd-c3323c4d7fc0	false	display.on.consent.screen
92c21645-ea9c-4433-abfd-c3323c4d7fc0	false	include.in.token.scope
9282382a-6d8c-4529-b174-cb7b87276a67	false	display.on.consent.screen
9282382a-6d8c-4529-b174-cb7b87276a67	false	include.in.token.scope
b5d3388f-c9b1-4bf6-aef0-b942f0015427	true	display.on.consent.screen
b5d3388f-c9b1-4bf6-aef0-b942f0015427	${organizationScopeConsentText}	consent.screen.text
b5d3388f-c9b1-4bf6-aef0-b942f0015427	true	include.in.token.scope
e8b8adf3-2e48-406e-b03c-ee798b047ba6	true	display.on.consent.screen
e8b8adf3-2e48-406e-b03c-ee798b047ba6	${offlineAccessScopeConsentText}	consent.screen.text
2fd5f4d6-e0d0-434b-b647-b441196a86af	true	display.on.consent.screen
2fd5f4d6-e0d0-434b-b647-b441196a86af	${samlRoleListScopeConsentText}	consent.screen.text
ca7f8f17-b3a2-4577-a6df-781b426a4052	false	display.on.consent.screen
c86b7616-505a-47db-acef-beb669c1a5ec	true	display.on.consent.screen
c86b7616-505a-47db-acef-beb669c1a5ec	${profileScopeConsentText}	consent.screen.text
c86b7616-505a-47db-acef-beb669c1a5ec	true	include.in.token.scope
89105071-a02d-448c-baaf-1197e4bb223d	true	display.on.consent.screen
89105071-a02d-448c-baaf-1197e4bb223d	${emailScopeConsentText}	consent.screen.text
89105071-a02d-448c-baaf-1197e4bb223d	true	include.in.token.scope
61d5c7ba-888d-4678-9834-ece659a1dc75	true	display.on.consent.screen
61d5c7ba-888d-4678-9834-ece659a1dc75	${addressScopeConsentText}	consent.screen.text
61d5c7ba-888d-4678-9834-ece659a1dc75	true	include.in.token.scope
195f10d2-6623-47d2-855e-7db6981c182d	true	display.on.consent.screen
195f10d2-6623-47d2-855e-7db6981c182d	${phoneScopeConsentText}	consent.screen.text
195f10d2-6623-47d2-855e-7db6981c182d	true	include.in.token.scope
c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	true	display.on.consent.screen
c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	${rolesScopeConsentText}	consent.screen.text
c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	false	include.in.token.scope
a9f69da8-e105-499f-a44e-22ecd6043f95	false	display.on.consent.screen
a9f69da8-e105-499f-a44e-22ecd6043f95		consent.screen.text
a9f69da8-e105-499f-a44e-22ecd6043f95	false	include.in.token.scope
5237f92a-3544-409d-a97e-551d5c8f27d7	false	display.on.consent.screen
5237f92a-3544-409d-a97e-551d5c8f27d7	true	include.in.token.scope
c579e87e-c2cd-443c-b5bc-f6e35dd4b259	false	display.on.consent.screen
c579e87e-c2cd-443c-b5bc-f6e35dd4b259	false	include.in.token.scope
f044e4ae-6203-4844-a78c-84fb60b0cd72	false	display.on.consent.screen
f044e4ae-6203-4844-a78c-84fb60b0cd72	false	include.in.token.scope
0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	true	display.on.consent.screen
0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	${organizationScopeConsentText}	consent.screen.text
0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	true	include.in.token.scope
b8a17460-770a-4aeb-aba3-c8e453f242f8	true	display.on.consent.screen
b8a17460-770a-4aeb-aba3-c8e453f242f8	${offlineAccessScopeConsentText}	consent.screen.text
6c136e2a-5769-4566-9c62-568b1bf54a20	true	display.on.consent.screen
6c136e2a-5769-4566-9c62-568b1bf54a20	${samlRoleListScopeConsentText}	consent.screen.text
d28411e0-ff79-4ab9-8c96-302b3ae89996	false	display.on.consent.screen
9bf8f3c7-2599-44d8-93c8-375927f6a67a	true	display.on.consent.screen
9bf8f3c7-2599-44d8-93c8-375927f6a67a	${profileScopeConsentText}	consent.screen.text
9bf8f3c7-2599-44d8-93c8-375927f6a67a	true	include.in.token.scope
604c75b6-95dd-45d1-af05-eb4c7ec83dc3	true	display.on.consent.screen
604c75b6-95dd-45d1-af05-eb4c7ec83dc3	${emailScopeConsentText}	consent.screen.text
604c75b6-95dd-45d1-af05-eb4c7ec83dc3	true	include.in.token.scope
d9af3ac6-a7da-484a-97ce-5e1e25badc5f	true	display.on.consent.screen
d9af3ac6-a7da-484a-97ce-5e1e25badc5f	${addressScopeConsentText}	consent.screen.text
d9af3ac6-a7da-484a-97ce-5e1e25badc5f	true	include.in.token.scope
38f5bf2d-dad0-449f-8d0c-438a4edb1540	true	display.on.consent.screen
38f5bf2d-dad0-449f-8d0c-438a4edb1540	${phoneScopeConsentText}	consent.screen.text
38f5bf2d-dad0-449f-8d0c-438a4edb1540	true	include.in.token.scope
9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	true	display.on.consent.screen
9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	${rolesScopeConsentText}	consent.screen.text
9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	false	include.in.token.scope
0d3d0b4f-8f6d-4b45-8914-b383d61ca069	false	display.on.consent.screen
0d3d0b4f-8f6d-4b45-8914-b383d61ca069		consent.screen.text
0d3d0b4f-8f6d-4b45-8914-b383d61ca069	false	include.in.token.scope
735ea178-7454-4888-b330-21b6c6c200f9	false	display.on.consent.screen
735ea178-7454-4888-b330-21b6c6c200f9	true	include.in.token.scope
29c4f76f-4ac1-42f3-8876-cd82b644632e	false	display.on.consent.screen
29c4f76f-4ac1-42f3-8876-cd82b644632e	false	include.in.token.scope
b8081550-653f-403f-8e2c-f5059baf4c44	false	display.on.consent.screen
b8081550-653f-403f-8e2c-f5059baf4c44	false	include.in.token.scope
b290d0cc-d348-4360-b168-8ac976c0085a	true	display.on.consent.screen
b290d0cc-d348-4360-b168-8ac976c0085a	${organizationScopeConsentText}	consent.screen.text
b290d0cc-d348-4360-b168-8ac976c0085a	true	include.in.token.scope
\.


--
-- Data for Name: client_scope_client; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_scope_client (client_id, scope_id, default_scope) FROM stdin;
3d56e973-75fb-4d57-bb78-302af297fe9d	a5930a85-5391-46e7-946b-887c57753f84	t
3d56e973-75fb-4d57-bb78-302af297fe9d	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
3d56e973-75fb-4d57-bb78-302af297fe9d	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
3d56e973-75fb-4d57-bb78-302af297fe9d	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
3d56e973-75fb-4d57-bb78-302af297fe9d	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
3d56e973-75fb-4d57-bb78-302af297fe9d	9282382a-6d8c-4529-b174-cb7b87276a67	t
3d56e973-75fb-4d57-bb78-302af297fe9d	17c8a13e-ea53-420e-b0cd-1b251905e541	f
3d56e973-75fb-4d57-bb78-302af297fe9d	5dec384e-eccb-4f03-8078-81b35b6319d6	f
3d56e973-75fb-4d57-bb78-302af297fe9d	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
3d56e973-75fb-4d57-bb78-302af297fe9d	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
3d56e973-75fb-4d57-bb78-302af297fe9d	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
1468f635-7aee-4d08-9633-86593942e889	a5930a85-5391-46e7-946b-887c57753f84	t
1468f635-7aee-4d08-9633-86593942e889	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
1468f635-7aee-4d08-9633-86593942e889	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
1468f635-7aee-4d08-9633-86593942e889	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
1468f635-7aee-4d08-9633-86593942e889	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
1468f635-7aee-4d08-9633-86593942e889	9282382a-6d8c-4529-b174-cb7b87276a67	t
1468f635-7aee-4d08-9633-86593942e889	17c8a13e-ea53-420e-b0cd-1b251905e541	f
1468f635-7aee-4d08-9633-86593942e889	5dec384e-eccb-4f03-8078-81b35b6319d6	f
1468f635-7aee-4d08-9633-86593942e889	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
1468f635-7aee-4d08-9633-86593942e889	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
1468f635-7aee-4d08-9633-86593942e889	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	a5930a85-5391-46e7-946b-887c57753f84	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	9282382a-6d8c-4529-b174-cb7b87276a67	t
6909eac5-21f8-47b5-b033-0915b4c3bbcf	17c8a13e-ea53-420e-b0cd-1b251905e541	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	5dec384e-eccb-4f03-8078-81b35b6319d6	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
6909eac5-21f8-47b5-b033-0915b4c3bbcf	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	a5930a85-5391-46e7-946b-887c57753f84	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	9282382a-6d8c-4529-b174-cb7b87276a67	t
f4202c47-7418-4e21-b20a-b430e5a8e6e8	17c8a13e-ea53-420e-b0cd-1b251905e541	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	5dec384e-eccb-4f03-8078-81b35b6319d6	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
f4202c47-7418-4e21-b20a-b430e5a8e6e8	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
21023f50-efbf-4680-94a0-dd36232f70b2	a5930a85-5391-46e7-946b-887c57753f84	t
21023f50-efbf-4680-94a0-dd36232f70b2	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
21023f50-efbf-4680-94a0-dd36232f70b2	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
21023f50-efbf-4680-94a0-dd36232f70b2	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
21023f50-efbf-4680-94a0-dd36232f70b2	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
21023f50-efbf-4680-94a0-dd36232f70b2	9282382a-6d8c-4529-b174-cb7b87276a67	t
21023f50-efbf-4680-94a0-dd36232f70b2	17c8a13e-ea53-420e-b0cd-1b251905e541	f
21023f50-efbf-4680-94a0-dd36232f70b2	5dec384e-eccb-4f03-8078-81b35b6319d6	f
21023f50-efbf-4680-94a0-dd36232f70b2	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
21023f50-efbf-4680-94a0-dd36232f70b2	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
21023f50-efbf-4680-94a0-dd36232f70b2	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	a5930a85-5391-46e7-946b-887c57753f84	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	9282382a-6d8c-4529-b174-cb7b87276a67	t
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	17c8a13e-ea53-420e-b0cd-1b251905e541	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	5dec384e-eccb-4f03-8078-81b35b6319d6	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	a5930a85-5391-46e7-946b-887c57753f84	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	9282382a-6d8c-4529-b174-cb7b87276a67	t
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	17c8a13e-ea53-420e-b0cd-1b251905e541	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	5dec384e-eccb-4f03-8078-81b35b6319d6	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	c86b7616-505a-47db-acef-beb669c1a5ec	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	a9f69da8-e105-499f-a44e-22ecd6043f95	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	89105071-a02d-448c-baaf-1197e4bb223d	t
d022332e-3f8b-44dc-9e5c-c6751920ab3d	61d5c7ba-888d-4678-9834-ece659a1dc75	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	5237f92a-3544-409d-a97e-551d5c8f27d7	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
d022332e-3f8b-44dc-9e5c-c6751920ab3d	195f10d2-6623-47d2-855e-7db6981c182d	f
d2dfba85-0770-401f-9388-86bb979ab264	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
d2dfba85-0770-401f-9388-86bb979ab264	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
d2dfba85-0770-401f-9388-86bb979ab264	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
d2dfba85-0770-401f-9388-86bb979ab264	c86b7616-505a-47db-acef-beb669c1a5ec	t
d2dfba85-0770-401f-9388-86bb979ab264	a9f69da8-e105-499f-a44e-22ecd6043f95	t
d2dfba85-0770-401f-9388-86bb979ab264	89105071-a02d-448c-baaf-1197e4bb223d	t
d2dfba85-0770-401f-9388-86bb979ab264	61d5c7ba-888d-4678-9834-ece659a1dc75	f
d2dfba85-0770-401f-9388-86bb979ab264	5237f92a-3544-409d-a97e-551d5c8f27d7	f
d2dfba85-0770-401f-9388-86bb979ab264	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
d2dfba85-0770-401f-9388-86bb979ab264	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
d2dfba85-0770-401f-9388-86bb979ab264	195f10d2-6623-47d2-855e-7db6981c182d	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	c86b7616-505a-47db-acef-beb669c1a5ec	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	a9f69da8-e105-499f-a44e-22ecd6043f95	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	89105071-a02d-448c-baaf-1197e4bb223d	t
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	61d5c7ba-888d-4678-9834-ece659a1dc75	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	5237f92a-3544-409d-a97e-551d5c8f27d7	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
525b3c0d-7cc4-4fc6-93fe-ef04792887f3	195f10d2-6623-47d2-855e-7db6981c182d	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	c86b7616-505a-47db-acef-beb669c1a5ec	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	a9f69da8-e105-499f-a44e-22ecd6043f95	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	89105071-a02d-448c-baaf-1197e4bb223d	t
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	61d5c7ba-888d-4678-9834-ece659a1dc75	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	5237f92a-3544-409d-a97e-551d5c8f27d7	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	195f10d2-6623-47d2-855e-7db6981c182d	f
950ca38d-f174-4392-adef-9153ec984d2b	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
950ca38d-f174-4392-adef-9153ec984d2b	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
950ca38d-f174-4392-adef-9153ec984d2b	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
950ca38d-f174-4392-adef-9153ec984d2b	c86b7616-505a-47db-acef-beb669c1a5ec	t
950ca38d-f174-4392-adef-9153ec984d2b	a9f69da8-e105-499f-a44e-22ecd6043f95	t
950ca38d-f174-4392-adef-9153ec984d2b	89105071-a02d-448c-baaf-1197e4bb223d	t
950ca38d-f174-4392-adef-9153ec984d2b	61d5c7ba-888d-4678-9834-ece659a1dc75	f
950ca38d-f174-4392-adef-9153ec984d2b	5237f92a-3544-409d-a97e-551d5c8f27d7	f
950ca38d-f174-4392-adef-9153ec984d2b	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
950ca38d-f174-4392-adef-9153ec984d2b	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
950ca38d-f174-4392-adef-9153ec984d2b	195f10d2-6623-47d2-855e-7db6981c182d	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	c86b7616-505a-47db-acef-beb669c1a5ec	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	a9f69da8-e105-499f-a44e-22ecd6043f95	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	89105071-a02d-448c-baaf-1197e4bb223d	t
cbcbcbde-a715-4358-a3c3-7f027c4436b6	61d5c7ba-888d-4678-9834-ece659a1dc75	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	5237f92a-3544-409d-a97e-551d5c8f27d7	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
cbcbcbde-a715-4358-a3c3-7f027c4436b6	195f10d2-6623-47d2-855e-7db6981c182d	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	c86b7616-505a-47db-acef-beb669c1a5ec	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	a9f69da8-e105-499f-a44e-22ecd6043f95	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	89105071-a02d-448c-baaf-1197e4bb223d	t
2d441a2b-1316-4adc-b51a-8e2520fc33e6	61d5c7ba-888d-4678-9834-ece659a1dc75	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	5237f92a-3544-409d-a97e-551d5c8f27d7	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
2d441a2b-1316-4adc-b51a-8e2520fc33e6	195f10d2-6623-47d2-855e-7db6981c182d	f
81b24093-5b2e-4796-9adc-970cd6e97d98	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
81b24093-5b2e-4796-9adc-970cd6e97d98	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
81b24093-5b2e-4796-9adc-970cd6e97d98	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
81b24093-5b2e-4796-9adc-970cd6e97d98	c86b7616-505a-47db-acef-beb669c1a5ec	t
81b24093-5b2e-4796-9adc-970cd6e97d98	a9f69da8-e105-499f-a44e-22ecd6043f95	t
81b24093-5b2e-4796-9adc-970cd6e97d98	89105071-a02d-448c-baaf-1197e4bb223d	t
81b24093-5b2e-4796-9adc-970cd6e97d98	61d5c7ba-888d-4678-9834-ece659a1dc75	f
81b24093-5b2e-4796-9adc-970cd6e97d98	5237f92a-3544-409d-a97e-551d5c8f27d7	f
81b24093-5b2e-4796-9adc-970cd6e97d98	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
81b24093-5b2e-4796-9adc-970cd6e97d98	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
81b24093-5b2e-4796-9adc-970cd6e97d98	195f10d2-6623-47d2-855e-7db6981c182d	f
4a7c7844-cde9-4654-b214-b2bd003f2344	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
4a7c7844-cde9-4654-b214-b2bd003f2344	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
4a7c7844-cde9-4654-b214-b2bd003f2344	b8081550-653f-403f-8e2c-f5059baf4c44	t
4a7c7844-cde9-4654-b214-b2bd003f2344	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
4a7c7844-cde9-4654-b214-b2bd003f2344	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
4a7c7844-cde9-4654-b214-b2bd003f2344	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
4a7c7844-cde9-4654-b214-b2bd003f2344	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
4a7c7844-cde9-4654-b214-b2bd003f2344	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
4a7c7844-cde9-4654-b214-b2bd003f2344	b290d0cc-d348-4360-b168-8ac976c0085a	f
4a7c7844-cde9-4654-b214-b2bd003f2344	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
4a7c7844-cde9-4654-b214-b2bd003f2344	735ea178-7454-4888-b330-21b6c6c200f9	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	b8081550-653f-403f-8e2c-f5059baf4c44	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
cba0a035-784d-4ec0-9c98-64f8bb89002b	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	b290d0cc-d348-4360-b168-8ac976c0085a	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
cba0a035-784d-4ec0-9c98-64f8bb89002b	735ea178-7454-4888-b330-21b6c6c200f9	f
e932343c-9370-4ae5-878d-a4f008065947	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
e932343c-9370-4ae5-878d-a4f008065947	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
e932343c-9370-4ae5-878d-a4f008065947	b8081550-653f-403f-8e2c-f5059baf4c44	t
e932343c-9370-4ae5-878d-a4f008065947	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
e932343c-9370-4ae5-878d-a4f008065947	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
e932343c-9370-4ae5-878d-a4f008065947	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
e932343c-9370-4ae5-878d-a4f008065947	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
e932343c-9370-4ae5-878d-a4f008065947	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
e932343c-9370-4ae5-878d-a4f008065947	b290d0cc-d348-4360-b168-8ac976c0085a	f
e932343c-9370-4ae5-878d-a4f008065947	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
e932343c-9370-4ae5-878d-a4f008065947	735ea178-7454-4888-b330-21b6c6c200f9	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	b8081550-653f-403f-8e2c-f5059baf4c44	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
97ba8722-5833-4ae0-bba0-a3359d112f8d	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	b290d0cc-d348-4360-b168-8ac976c0085a	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
97ba8722-5833-4ae0-bba0-a3359d112f8d	735ea178-7454-4888-b330-21b6c6c200f9	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	b8081550-653f-403f-8e2c-f5059baf4c44	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
8184fc2e-49bc-4c82-a490-135cebf3e73c	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	b290d0cc-d348-4360-b168-8ac976c0085a	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
8184fc2e-49bc-4c82-a490-135cebf3e73c	735ea178-7454-4888-b330-21b6c6c200f9	f
d5442a0f-3191-407b-befa-391aaa28773d	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
d5442a0f-3191-407b-befa-391aaa28773d	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
d5442a0f-3191-407b-befa-391aaa28773d	b8081550-653f-403f-8e2c-f5059baf4c44	t
d5442a0f-3191-407b-befa-391aaa28773d	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
d5442a0f-3191-407b-befa-391aaa28773d	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
d5442a0f-3191-407b-befa-391aaa28773d	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
d5442a0f-3191-407b-befa-391aaa28773d	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
d5442a0f-3191-407b-befa-391aaa28773d	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
d5442a0f-3191-407b-befa-391aaa28773d	b290d0cc-d348-4360-b168-8ac976c0085a	f
d5442a0f-3191-407b-befa-391aaa28773d	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
d5442a0f-3191-407b-befa-391aaa28773d	735ea178-7454-4888-b330-21b6c6c200f9	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	b8081550-653f-403f-8e2c-f5059baf4c44	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
2e5dede9-f6b2-489c-8c2b-44ca02f30878	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	b290d0cc-d348-4360-b168-8ac976c0085a	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
2e5dede9-f6b2-489c-8c2b-44ca02f30878	735ea178-7454-4888-b330-21b6c6c200f9	f
5907c420-e9c0-4884-85ed-a753a9be228e	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
5907c420-e9c0-4884-85ed-a753a9be228e	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
5907c420-e9c0-4884-85ed-a753a9be228e	b8081550-653f-403f-8e2c-f5059baf4c44	t
5907c420-e9c0-4884-85ed-a753a9be228e	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
5907c420-e9c0-4884-85ed-a753a9be228e	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
5907c420-e9c0-4884-85ed-a753a9be228e	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
5907c420-e9c0-4884-85ed-a753a9be228e	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
5907c420-e9c0-4884-85ed-a753a9be228e	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
5907c420-e9c0-4884-85ed-a753a9be228e	b290d0cc-d348-4360-b168-8ac976c0085a	f
5907c420-e9c0-4884-85ed-a753a9be228e	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
5907c420-e9c0-4884-85ed-a753a9be228e	735ea178-7454-4888-b330-21b6c6c200f9	f
\.


--
-- Data for Name: client_scope_role_mapping; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.client_scope_role_mapping (scope_id, role_id) FROM stdin;
17c8a13e-ea53-420e-b0cd-1b251905e541	ae38ddb2-41d2-482e-85f2-edc9118bf2c2
e8b8adf3-2e48-406e-b03c-ee798b047ba6	1bb8eaf2-9cfb-445a-95bc-a5d892057ed7
b8a17460-770a-4aeb-aba3-c8e453f242f8	62846123-b3ae-4585-9974-8f1b618cc069
\.


--
-- Data for Name: component; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.component (id, name, parent_id, provider_id, provider_type, realm_id, sub_type) FROM stdin;
b9b2ae2c-98a2-4d8d-a74f-eabf67b89446	Trusted Hosts	259bfe83-5374-4ae7-bb38-5464d0d5bc75	trusted-hosts	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
7e391cb2-0719-4ab8-88a2-a8ed695489b1	Consent Required	259bfe83-5374-4ae7-bb38-5464d0d5bc75	consent-required	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
d52c539e-5ac9-4dcc-a59d-7cb3a08a8ee4	Full Scope Disabled	259bfe83-5374-4ae7-bb38-5464d0d5bc75	scope	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
0820204b-67cb-4d8c-b8e7-ab16252ebd09	Max Clients Limit	259bfe83-5374-4ae7-bb38-5464d0d5bc75	max-clients	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
3f980cdf-dc08-49c9-a909-74423fc01338	Allowed Protocol Mapper Types	259bfe83-5374-4ae7-bb38-5464d0d5bc75	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
f9087ad7-4026-44d0-ab43-049652a47923	Allowed Client Scopes	259bfe83-5374-4ae7-bb38-5464d0d5bc75	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	anonymous
9a7bc6a2-a84d-4998-b770-804c02025601	Allowed Protocol Mapper Types	259bfe83-5374-4ae7-bb38-5464d0d5bc75	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	authenticated
aef3c2dc-ba08-4df1-8207-b9a988fe8d2d	Allowed Client Scopes	259bfe83-5374-4ae7-bb38-5464d0d5bc75	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	authenticated
8cef1fc4-5610-4992-8821-1628044ae427	rsa-generated	259bfe83-5374-4ae7-bb38-5464d0d5bc75	rsa-generated	org.keycloak.keys.KeyProvider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N
a84d3465-9633-45d7-94bf-53aacb09efdf	rsa-enc-generated	259bfe83-5374-4ae7-bb38-5464d0d5bc75	rsa-enc-generated	org.keycloak.keys.KeyProvider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N
30aeb5bf-cb1a-4a5c-bb94-555161e6de45	hmac-generated-hs512	259bfe83-5374-4ae7-bb38-5464d0d5bc75	hmac-generated	org.keycloak.keys.KeyProvider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N
046f65b1-fec1-4393-a600-2903d836ad15	aes-generated	259bfe83-5374-4ae7-bb38-5464d0d5bc75	aes-generated	org.keycloak.keys.KeyProvider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N
7cd00664-75fc-4769-8c1c-bdf8b30ea737	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	declarative-user-profile	org.keycloak.userprofile.UserProfileProvider	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N
214182fe-95fb-4899-93c0-80833c452f04	rsa-generated	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	rsa-generated	org.keycloak.keys.KeyProvider	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N
e21652c3-e12f-41a1-b1ac-b915903efd7a	rsa-enc-generated	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	rsa-enc-generated	org.keycloak.keys.KeyProvider	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N
6b2af300-66af-4f9f-8c6d-6bfae89d8ba5	hmac-generated-hs512	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	hmac-generated	org.keycloak.keys.KeyProvider	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N
f4b13ce0-3ba7-4e9b-9083-ca6039e7b010	aes-generated	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	aes-generated	org.keycloak.keys.KeyProvider	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N
4aa331e9-4455-4f72-9e63-4b2b38df59ab	Trusted Hosts	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	trusted-hosts	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
2a9b6e21-3c4f-491a-aa87-366b0e2b33da	Consent Required	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	consent-required	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
6a3c4413-1a39-489c-8a05-a3baaf2792cb	Full Scope Disabled	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	scope	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
bcc72ee5-48f0-4f3f-896c-1f64d3c8569a	Max Clients Limit	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	max-clients	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
ab57c38e-d58d-4f77-a0c3-71d8514ab88b	Allowed Protocol Mapper Types	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
2076fe25-fbf9-4148-9e5c-544920fdcad4	Allowed Client Scopes	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	anonymous
b2d62117-5193-4470-b337-db309935b401	Allowed Protocol Mapper Types	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	authenticated
8cdafa32-1dec-4a4e-acaf-f10de2511f19	Allowed Client Scopes	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	authenticated
33840fc2-dad6-4458-b0a8-aaa657c130f3	rsa-generated	83e82497-9a85-4600-9b3d-877c38c4ff59	rsa-generated	org.keycloak.keys.KeyProvider	83e82497-9a85-4600-9b3d-877c38c4ff59	\N
19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	rsa-enc-generated	83e82497-9a85-4600-9b3d-877c38c4ff59	rsa-enc-generated	org.keycloak.keys.KeyProvider	83e82497-9a85-4600-9b3d-877c38c4ff59	\N
ba4ace38-8d9a-491a-8aa3-2597ccee9795	hmac-generated-hs512	83e82497-9a85-4600-9b3d-877c38c4ff59	hmac-generated	org.keycloak.keys.KeyProvider	83e82497-9a85-4600-9b3d-877c38c4ff59	\N
3dd90a25-ad3f-4a34-8281-99760176a67b	aes-generated	83e82497-9a85-4600-9b3d-877c38c4ff59	aes-generated	org.keycloak.keys.KeyProvider	83e82497-9a85-4600-9b3d-877c38c4ff59	\N
b0203c07-4b2e-45dc-ba44-022cbb7a7e88	Trusted Hosts	83e82497-9a85-4600-9b3d-877c38c4ff59	trusted-hosts	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
5979733b-d0d6-4b1a-b92f-e037cedd4e32	Consent Required	83e82497-9a85-4600-9b3d-877c38c4ff59	consent-required	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
7519d706-fc0a-4750-8f05-bc6fde555799	Full Scope Disabled	83e82497-9a85-4600-9b3d-877c38c4ff59	scope	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
9582f3c5-45d7-4e17-aac7-d41804db5b2e	Max Clients Limit	83e82497-9a85-4600-9b3d-877c38c4ff59	max-clients	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
cd69696c-24b6-4406-875d-4bc00ef842b3	Allowed Protocol Mapper Types	83e82497-9a85-4600-9b3d-877c38c4ff59	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
078702f2-8ef1-4e97-a870-871a0af3e9f5	Allowed Client Scopes	83e82497-9a85-4600-9b3d-877c38c4ff59	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	anonymous
393014e9-4004-47de-a74f-9b52d554c92c	Allowed Protocol Mapper Types	83e82497-9a85-4600-9b3d-877c38c4ff59	allowed-protocol-mappers	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	authenticated
14977b85-874e-4670-9ed0-1726daf1c1c9	Allowed Client Scopes	83e82497-9a85-4600-9b3d-877c38c4ff59	allowed-client-templates	org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	authenticated
86094bde-109d-4773-90e2-8c87b7ee1ebf	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	declarative-user-profile	org.keycloak.userprofile.UserProfileProvider	83e82497-9a85-4600-9b3d-877c38c4ff59	\N
\.


--
-- Data for Name: component_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.component_config (id, component_id, name, value) FROM stdin;
7b201f18-a048-4cb5-9cc4-d32541f10ced	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	saml-role-list-mapper
fe960b44-0b07-487f-bb0b-f9b3d0774c25	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
b0fc23c5-2248-477b-a0aa-43d2fb4d1f3b	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
aa3a421b-fc5e-49a4-9097-14583355e216	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	saml-user-property-mapper
9da4d198-e137-44ae-96f9-6a5c138544ad	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	oidc-address-mapper
b76e8df5-f514-4192-8edb-9014f71011fa	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
6e1d2e95-a691-4c76-888c-5996a4dbdc5a	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	saml-user-attribute-mapper
187d2bbd-26c9-4f7d-b8aa-aa267ed6c43a	9a7bc6a2-a84d-4998-b770-804c02025601	allowed-protocol-mapper-types	oidc-full-name-mapper
04a54c5a-6c4e-4240-9da1-8215413c5f6b	f9087ad7-4026-44d0-ab43-049652a47923	allow-default-scopes	true
9b91150b-a218-4aaf-9475-e6d0a8b206ac	0820204b-67cb-4d8c-b8e7-ab16252ebd09	max-clients	200
213d78f9-e9a7-4c46-9cac-b7b17392aee9	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	saml-role-list-mapper
f38faabb-1f85-4afc-8f45-07dcb72cc2ba	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
aa3a7306-724d-4418-86ae-0ed1e71aaa4e	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	saml-user-attribute-mapper
6091607e-b773-4489-89bf-c0f9c9d38d34	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	oidc-address-mapper
853f57c0-67dc-419d-9e81-06c1d2ba932c	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
5c63c166-d06b-42c3-a104-f860d3d33249	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	oidc-full-name-mapper
1de0ce6d-9b37-45de-a57c-73ebdba789d5	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
f3e2a501-e33b-4344-b02c-2a5ddf2fef2a	3f980cdf-dc08-49c9-a909-74423fc01338	allowed-protocol-mapper-types	saml-user-property-mapper
13b60dfa-3587-424f-9671-33fc93c9fcef	b9b2ae2c-98a2-4d8d-a74f-eabf67b89446	client-uris-must-match	true
cd981312-8137-4b19-94c2-72e95732026e	b9b2ae2c-98a2-4d8d-a74f-eabf67b89446	host-sending-registration-request-must-match	true
9776d35a-1f11-4a5b-ae37-6f5efa23da89	aef3c2dc-ba08-4df1-8207-b9a988fe8d2d	allow-default-scopes	true
9c635951-8de0-48cc-ad7d-05ad968e8430	8cef1fc4-5610-4992-8821-1628044ae427	privateKey	MIIEowIBAAKCAQEAqNRfwlGCXU3Y/5Lc6idMGdqHJ1PKPEFWJ11eiXlFQMaTaU1RYAQyPAIfgd4VC5ILUKCdp2pNE2Le8dDJC3SVOQgAyqiiQeqe9MPQChmhDCyVZununCl6tgY34XZBvMBXYjwFoT+CyEwEzlJoJWGVhKn7Fypjgx2X1Cxvk/r1hxfiNyX6FnAdSetQSCfCoYYfLwD2hzRpO9wjoq9Rzj79QI3iIjECa3up/2MkUeYqNjmy0MlBfWU15L1hEte2TG1Kc9BEcsKx/wvjZIQ4vaWfJjyZJ/vOlkqFra0TPgAWGrZea9JSZRpXgbQAm/HPZXkYI62UwnvNCkI1DHdLCKQInwIDAQABAoIBAAEyqI9HZsZktAJ0N08OAlM4orbbuFafHA5hcjdFfgFMp2Ua4FXvEbAtT63rNnkDi0MCMLwsW+mkTPKoSZTkOd7SuLa3ZZdUI9aWwRYhQmxEsLavODsxbCZ4etMFtMezEaunw152heYH7AzOtuB1BcuQLTDn4xbeEeK2ggApyq4vsTywOs/v8RejalbJuv30UrPEx73JJTf/1c41BB9sQUNIJR/nYmngPj+dzMXpSkrcqVZhef9cxzgiwidAQm6mHVAVUpQujxJilkV7OJkcN4LlnzpjdtCcgU9erkcsC1KtrhcTv8QYZHjU6eys0m+E6SVpsGwhnXF6tC02BY1n2ykCgYEA4KryehirvblBDwg/X1KzcpO5JSNDZnZLxrqu52yaIfxAxF2OI4N14S35YTO0p0ZFLPk5PtVl7papKx1DHuJFX5ZPkcxorteVpwuE9JOFJtX0sWwIkAHVUmI/f15yx4XzKN4Z0EmWrBcqnpIIvBaX+NPYphLLTwrt+PjIQSmiYZUCgYEAwF/msxlt7C7wvHVFjMWQzHy8QEmT965pYdZ7jey42VHXE9TfQU8pgDlwJ3mMPO9uFPVk3DUO7IN+Ti2CDC3YqT4MK32JOAlewHBmLE8dYAb8aae/7rXdS5ZOZA64huN83ibb1Dkn99FGWgHKvCKlTEcdJLzhuWdMt3G3bUh8HGMCgYEAt3uFKigJ4plwQ6mVr/DnsvRoKadh/UUxX6zI+SHdw1GzR2uskN5lIDEe0L6clFw0VKV5lhJL/A25PQfZ8FgVFhq5Azawa9KPWuxyehgcj3n/LAMx/wIwSOcKOqhVDxQbYU36QPxIfGUzHmvfjFTbapEBB6ijt/sSHNp5GZa6Ef0CgYBdh4LLesaVuPC0P7Pz0C4pLU/9VYiKOBLmTXg+tWPdzMtryehNgWZlAAgGOTM0gRgdOn2yDl3WIcwryDZdI4EeL5uNHXi0dOHLYBpc2o4mnN7lo3hOX79au5YbUYjnk/ymqW/hi3RiKS4yHoLp21JQdpa1wNth39ZNrg0HJeGpjwKBgBMjtcZCDKEyHcorbf922gaOMn9ZxcZu23O30B6di0+VSyKfRkew9IbN9iloM9CoW1mrINFTijkBpNeoh2wCOfMNHrEtnLHIXVXs95PqQMEK3bgrDFJjEsmX5yxtWsfe1HeK76H8H1BEAlxeygYMXXD7grrs6VKEyb2FnbyBZZYX
a9bb97b8-67ad-4d8a-a574-f9e0f413596b	8cef1fc4-5610-4992-8821-1628044ae427	keyUse	SIG
a2c6f286-459e-4264-96be-a2c92f7ad63c	8cef1fc4-5610-4992-8821-1628044ae427	priority	100
7e54fc90-306d-494c-83f2-5123e1b63105	8cef1fc4-5610-4992-8821-1628044ae427	certificate	MIICmzCCAYMCBgGfExLw6zANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjYwNjI5MTEwODU2WhcNMzYwNjI5MTExMDM2WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCo1F/CUYJdTdj/ktzqJ0wZ2ocnU8o8QVYnXV6JeUVAxpNpTVFgBDI8Ah+B3hULkgtQoJ2nak0TYt7x0MkLdJU5CADKqKJB6p70w9AKGaEMLJVm6e6cKXq2BjfhdkG8wFdiPAWhP4LITATOUmglYZWEqfsXKmODHZfULG+T+vWHF+I3JfoWcB1J61BIJ8Khhh8vAPaHNGk73COir1HOPv1AjeIiMQJre6n/YyRR5io2ObLQyUF9ZTXkvWES17ZMbUpz0ERywrH/C+NkhDi9pZ8mPJkn+86WSoWtrRM+ABYatl5r0lJlGleBtACb8c9leRgjrZTCe80KQjUMd0sIpAifAgMBAAEwDQYJKoZIhvcNAQELBQADggEBABySbNFVGWG1Fk/R0eXtcG3j41FaBQUWsFfbe17G/0z0PbD8RK8GwIB8psEvESQviVy1W6cXnbx5CDzjOMvwctq5uOtDl29hZx66fqpaqbJI9Zn0yLyErr9AvAw7e9dNnIrl3j10+Hca4BRKE9Wcm/FgQYCmMmMXG0v5KG5rp9iPy7JUaK192h5dGYoISlVmZ0d1IGooZXz1uvnMaR3d8x8/vtpuk/PZDuHyVTIEJLD6/COPXo7/CT9y2d+oGQi16cnxRngJNheUovlBiZvubFQYhmPKIlJ3D4it5/SP9oSLFh9dBx8xXjMT6pcDLcHLdtAFrl1IcxhDXbIHKjf97nc=
06c878f8-6d12-48e6-9dd1-b72724b7db14	a84d3465-9633-45d7-94bf-53aacb09efdf	algorithm	RSA-OAEP
22914989-02d7-46cd-b9f2-2eeb04d678ff	a84d3465-9633-45d7-94bf-53aacb09efdf	privateKey	MIIEowIBAAKCAQEAqFYsqSiy5rdfoGv7p+7bB8YFRBZLaO2ObgaBd8iXVvYuZR9B/JdAkvsI1qJ0au5CjVN4jpzqEi4kZiSLl/alM2a9/KAdJSKwm46klE6VyIb82WFul2+UXkhPN+/bsooqV2DHJvobzfb/Osjkrg9lv80uN/FFCFquGKgnd7WT+pfzdRmXbutkTPvIkcNTz5EIpR1noeT94PsYZIzpSAe1xKLqxLaNDsicB5wu+6ON75WNjXPx3PIyDgqdB9dg7cDgkV4mIVtjnj2Qmhlz/sI/SZyP7AEoDOqnqadlP02bF/x0ScY5RRMUU/kx3oQUaRmL6oka9X5H0Kj9J00rmnR8IwIDAQABAoIBAAOH6zU7zqTa69KS8UW7KN+niQITRaRUd0xaLpUIoW6y3ejubZU9cZ6jorJ92IX1rB0IHIXr8gJM8s6Brbvo8dt7fY4IUxQtqhlZNfKLw3CKrvsQTsrhDZz8qfdjvJlTdJfom8TMMjvGa+FxHVAur9UExMIU+XxiCozR2iIUrknvT/WSgCPC2x7kNeD6RZ3Y/PaH1177exNDvD/y+KciXOMCa4mM1oPrBPqrO+x2hJToigHheXSu4fggVpQHSbC94lxoRBlbIud1rnH4oqdO+xec6+WX/PTY6KzBxdU30Cg1OIbzCLQ2ZvthGRsvS5Yj3guIXM11rZ7aKDTwp+hcsKECgYEA2UeJf94n/2bCd5p/907nkgdSGjHjGgZR2x3O2nmUI0z9ByvTfp8q6dMfJQHVtHUZJFNj35VewkRw+RoA1KicZTCEw6V4uL9M0vBuTlduG30ZzAzoCGH+BsCmtqluXVdXnELSbILHW9o7UPop0TCu1RJNk5zBbQNvZv6i3TJAXZ0CgYEAxlXTj1KgV+kUbI30aiMQO9u4U14YWciCGRU6GNfSa3oDBl15BNwWND/e4k3FtZTYfCTOJX5jRMRz5CvQhFGjCPsNbxf2PCQblz2Ub2jy2s+8dm3kwOinEZPgKyxJAqcPeiMXnrcKUZhEncwxRe7FzouOJqp2wZ/TtZz1YlR59L8CgYBClSRyla5mGqRPKH48V+aoniuvsUfpqrQc1GaWFpmps6b6Mpjt/XKWEsdBfO1zDks1EYTSeCCAoZ6JB0nn4P+EFrkohyWWX7D4FwuGYE5yiASGvhHAt96zTQgJg6Q5YFGNpgB7lhvnbtmsd+ymmSeKTcCOEoRHlrzlr/RSmlqVFQKBgCv1zgSQbdXOZaaB+FCmIO0DkGF6E6+iz6M7nzrua0dDLfR1N2OqXImMNpy2XEIZEbrCdaa90sblPEKwq3EInzhYXOvoxb0iy7LEBWiEPH/fV4hwZymS56Tvv9oR4MTjvnx11sBpFbJsK5lGLw2vFMzpBcLwOY5FHKm+dm0rCDUFAoGBAM1fkBB8g70b1ftwOSpEIgocOcQSkJS4SnzTp8dNec0QCbh8Ff6wV0rTJ9ruUTSOPXPcWKn8VVde3grgqTG8fHodZGd0D5vwwmsTm7OLZzjWLN840XfUP8CeU1hIteoMqdp+dXn8/6nGDNL6liNSCmN8m57YxW1qqVgGSpwOrL3l
5ee69d1b-84a3-4851-9c73-3d83bef15dd9	a84d3465-9633-45d7-94bf-53aacb09efdf	certificate	MIICmzCCAYMCBgGfExLyDzANBgkqhkiG9w0BAQsFADARMQ8wDQYDVQQDDAZtYXN0ZXIwHhcNMjYwNjI5MTEwODU2WhcNMzYwNjI5MTExMDM2WjARMQ8wDQYDVQQDDAZtYXN0ZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCoViypKLLmt1+ga/un7tsHxgVEFkto7Y5uBoF3yJdW9i5lH0H8l0CS+wjWonRq7kKNU3iOnOoSLiRmJIuX9qUzZr38oB0lIrCbjqSUTpXIhvzZYW6Xb5ReSE8379uyiipXYMcm+hvN9v86yOSuD2W/zS438UUIWq4YqCd3tZP6l/N1GZdu62RM+8iRw1PPkQilHWeh5P3g+xhkjOlIB7XEourEto0OyJwHnC77o43vlY2Nc/Hc8jIOCp0H12DtwOCRXiYhW2OePZCaGXP+wj9JnI/sASgM6qepp2U/TZsX/HRJxjlFExRT+THehBRpGYvqiRr1fkfQqP0nTSuadHwjAgMBAAEwDQYJKoZIhvcNAQELBQADggEBABSDcAxo31UvO+rq+oSMuIy1k66wLT85GNQ2rDtrp+0MoW+HDjW6GiHyea6qqIFqJCDvDHzUAHAL6a4B+eBErssyJI0eF5gCl1IvNAEaqNXJRbszmmYYFE0rSCGBOdx9er7NbXvi5kuqQ+44MtQ7VfaEgTK2Hyw3xldRtr8CqElLXRebR92z0oZMj50FInnRU979SVHzyzAMUiOhxj27JKHvi03kTPV8lwrjGs8uxHVe2436m6lWT6iMrYMRggcofEc+4/IvhKcoQjDuSZEwe98TO6R/eSRxbYvnv4BcK5lJPiEcN2J+2NfQLAV26RLmIJyuN4eMilHYOqBpYxJTuf8=
59287a65-8d38-48c7-84f7-33d936e9dfb1	a84d3465-9633-45d7-94bf-53aacb09efdf	priority	100
1eb212ef-d071-4579-ab34-6dd9a6e58d01	a84d3465-9633-45d7-94bf-53aacb09efdf	keyUse	ENC
b301167b-2bcc-4d69-9c32-63eb7aa76b64	046f65b1-fec1-4393-a600-2903d836ad15	secret	7naw_PujZd7bwtn2_y59Dw
4253faa2-f3a7-4539-95bd-d2570dfd4320	046f65b1-fec1-4393-a600-2903d836ad15	priority	100
8b162604-bd9a-4cfd-8ada-0d3ea70f81ed	046f65b1-fec1-4393-a600-2903d836ad15	kid	0e51ee3d-ea92-43bb-ab97-9b62a8e8d140
733194bb-1db3-4a60-872a-2305a21d4844	7cd00664-75fc-4769-8c1c-bdf8b30ea737	kc.user.profile.config	{"attributes":[{"name":"username","displayName":"${username}","validations":{"length":{"min":3,"max":255},"username-prohibited-characters":{},"up-username-not-idn-homograph":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"email","displayName":"${email}","validations":{"email":{},"length":{"max":255}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"firstName","displayName":"${firstName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"lastName","displayName":"${lastName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":"Attributes, which refer to user metadata"}]}
26022a33-df05-41d2-ae5b-e9ccb93fa0f4	30aeb5bf-cb1a-4a5c-bb94-555161e6de45	priority	100
f85f6234-b4e2-4077-81b5-4627a48f0f4c	30aeb5bf-cb1a-4a5c-bb94-555161e6de45	secret	AjR4UMlZVa-lWdt6YIiV0KaO1rB7KJ_qylGsmMQ8Ld-0srMtoJZ2DB2S6DpTm0SRnhpFXb45BKrWCcxJ6FJPSJAkhWpUkNuv630pfnLym_QE5UdRmLoFe-W80b3LhSYOmNsUJWY8FxzlPppkxiKTbKEJebB5ZnOlQ0EL2zGrD3E
96a80040-92a5-4a06-b50f-fd7b9b252ded	30aeb5bf-cb1a-4a5c-bb94-555161e6de45	algorithm	HS512
a5efe355-3cce-4d30-a8fc-2d16d0d7121a	30aeb5bf-cb1a-4a5c-bb94-555161e6de45	kid	028413a5-4075-464a-9cd8-7973d8588106
ac2e01ca-8bbb-43d5-8792-0df04e88f110	f4b13ce0-3ba7-4e9b-9083-ca6039e7b010	secret	_mGDTf83nxpH0QPW5YN5vw
3ee316cc-fe71-4ab3-a389-e5e69a502ade	f4b13ce0-3ba7-4e9b-9083-ca6039e7b010	priority	100
13de1a45-8f6c-4cf4-aae0-e47298120cb6	f4b13ce0-3ba7-4e9b-9083-ca6039e7b010	kid	4e644488-c662-4b43-8dc6-7c9dcc7085b8
c553795e-7c5e-4c31-861c-1c8e9a24f377	214182fe-95fb-4899-93c0-80833c452f04	certificate	MIICrzCCAZcCBgGfGC6WWTANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQDDBBwbGF0Zm9ybWVfc2Nob29sMB4XDTI2MDYzMDEwNTcxNFoXDTM2MDYzMDEwNTg1NFowGzEZMBcGA1UEAwwQcGxhdGZvcm1lX3NjaG9vbDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMBiw655yiladMzms9WNjR2TIUQ7i4IKzAaWX3foGFKlAofsz/s53/D3ou6dX17eSwd8OsfLx6x0QsaF1TK1u24aUyt9jwLR2BbhktOYKMExargWXBjrtuNFwAweVqWhPBLSNXLq9yxRaRimRGVdnLwR2xopm0T5imkmWcys8DpBlOyeZYhk+pndhoT42fud2OK4w213JJiiMTFBB+0u0VJiQBIZrFNE89Q0KnOvmmIE8fHmyjEzTVEaBvm1n84cOVEBodt+6RmVmnzOTAqcrThVxRdmB+6WTGmf5gRDk8Szhjl/nmxrWLkihQec+2oFLAaevCMHbVcnqHJERQvlLG0CAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAUh0ulwtTz0AonCseUkC6BxNcMRlNFEpXzSjvfNhsnISocwgj2FWfZHjohSyZQ+EFtXX6EswwwFmPxUPyu160g3Wmss7uLXmt8Zouck77wIJdDeUHSqkqZ/xijEkcpu25dW1WromI4fFgeLFkG9d+0ajYl4UzVtSl/8lfFViWSzEOBpXQ/YP1NQjgqgh7YJCYXOagEhYw8fmXXf2Bx4oCrrJfuXJRqoJ0QBhljRR3SvsNFO97rECbigaX6y9eCiXQjhC2mtBBBHjtpO5XeLAvd/FzsHdC6v7X4cQtARrbW4qw3RiI7ZpxJQ0OUqHjKl3Vj7qCbqAOE503ZQmWcDkvyQ==
cbd8b922-4762-47c8-9853-1d0615852e9e	214182fe-95fb-4899-93c0-80833c452f04	priority	100
7bc3768f-b938-42a5-ab16-dd71bb78ddfb	214182fe-95fb-4899-93c0-80833c452f04	privateKey	MIIEowIBAAKCAQEAwGLDrnnKKVp0zOaz1Y2NHZMhRDuLggrMBpZfd+gYUqUCh+zP+znf8Pei7p1fXt5LB3w6x8vHrHRCxoXVMrW7bhpTK32PAtHYFuGS05gowTFquBZcGOu240XADB5WpaE8EtI1cur3LFFpGKZEZV2cvBHbGimbRPmKaSZZzKzwOkGU7J5liGT6md2GhPjZ+53Y4rjDbXckmKIxMUEH7S7RUmJAEhmsU0Tz1DQqc6+aYgTx8ebKMTNNURoG+bWfzhw5UQGh237pGZWafM5MCpytOFXFF2YH7pZMaZ/mBEOTxLOGOX+ebGtYuSKFB5z7agUsBp68IwdtVyeockRFC+UsbQIDAQABAoIBAAGtyYIYuoeKZnMUZQqhld6rYe5Ml9D6yph18GJuICpk0+C8U6vxqc3Oy2q+j+t/YaewnNduanQdFcwXtOR9wgwleEzg8vxEfuRX3IBsv/TXpAKuVdEMZv8sMGDB978O0ceooWgMUoGA9LuO+Jzq7k4JHpT+3Zcw9HgqnoKKSOYGUpzlr9hubeSr3atDeXdXOQD6h0tZ5jxjm1UJps2boh3bymFkaUNxU3pa9OriKOxyHyiqDPgyNf7hO8YBse3ZywhFhj4LO8DhhmKXUhYeESDymmNwHfx4vFXQltFF4S45812FMDxcm0a/3o3UWj8CuftyFRsl0XC2i5e0FZ/ihh0CgYEA3iXn5DN29QFEpRGQFK0MpymeL4D4O9Vq9Q3iaz+4WB5u/pxmb7C19ih7KCvr9YyJoEwXS9NiCHg/L8ngIgQkUfGtBhsWrvb3R7Nrap+J6CWmX6cg5q3UzCg8QMdKkquFmMOax4mY4xHZMmFG0yqjn5PaSGnEkQROIWhvlNAnN0cCgYEA3bPRuNMooye9t+LNpc/TVDjYXsBMOk3ta5+Scd/ZUW3bdyIrUDuqspzWgua3pPhBBR5UuU03HoOpwdZs0SDPQ1m1t1TOIb/lApwvlsD9NuKLEdYu7NInkHLyUS/244H8lj97zDjq9L3D4GV5FCU6SHRdvXAWm7WeQ3+o4MDSwKsCgYANPXEetrqGS7cNp4hQ0yeH5wEYj7U7y7J+PH+cKMYR6m5vGl84lO2jaDI+6K89iUzCkwZobIbRPml1rbCAFoMbpy3KyV6Guw1t5pGmXy/W+Dd8R/e8B+xA5r0Al2AocbSasD3PWrIUBoJNcGLKPyZVYniGDFe81ObbMuBDnExaDQKBgG+X/8yknSPi8SAKTbFUtu8B3gdzi+sdJpCxtakUH/NY9Ms2q86q+fUF9QPW1kF3pGLLLaJIN6Joa55iLEvn0+v2z/0D9u4OBVwW+e1MHyjtR0snIF+cOMYA1e//0Kob1+RY+BuH2txuWJYu/Y8iXf/ycuzT6s1d0dfv/jWDWZIZAoGBANAQtgcRyDZFRczgErA17j1DCphT3ffGp70884KqwybgIfOGGXBxHZ4diT1lebYX6Qo8PvoWcBQX2wK0fr7IWVEyq51rWmkXv7ps93b7m7qcCPr3xgnX9U5UnQisKVEdq4aE3as42m7ECj4nY5tqUkouP66AqGJyikHKOalnXiV2
827ed544-5e8e-48d9-8c88-4a7854576c17	214182fe-95fb-4899-93c0-80833c452f04	keyUse	SIG
468a86d8-b694-4b65-8721-7d7bf3c2938a	6b2af300-66af-4f9f-8c6d-6bfae89d8ba5	priority	100
18a84eea-5682-4afd-8934-1e81a0c8d2d1	6b2af300-66af-4f9f-8c6d-6bfae89d8ba5	kid	74d67298-33e7-4afa-b83d-1d9a016b12cb
581eda2c-a1cc-4d72-9a24-4b6cf787c414	6b2af300-66af-4f9f-8c6d-6bfae89d8ba5	secret	uA-9DXfB_6C5CR-pUPWoFJjKUklUNpbZWtsOdqC1N66OGDHZDZJ_e2rmvVKEYVjcqEH1A6fm595zUHYG7Ie7VqRxjdzNv7eaCzsJnxBSo3eOH6RKck2g-wt2o72BKsIqgDitHkwFGTX8iOyv0f8oXYmP9T25a_nU_tn8HXdE91w
608431b6-3972-41de-9aa0-d55d926b46e9	6b2af300-66af-4f9f-8c6d-6bfae89d8ba5	algorithm	HS512
d347c915-8d35-42ff-aab6-320a71870bbd	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	saml-role-list-mapper
5f7c0c3f-2816-4d7d-bcb5-633b2405ed47	078702f2-8ef1-4e97-a870-871a0af3e9f5	allow-default-scopes	true
ee3382be-58a7-4caa-854f-3b56a4849c36	9582f3c5-45d7-4e17-aac7-d41804db5b2e	max-clients	200
6412e596-cc3b-412b-966b-2c3ed155235a	b0203c07-4b2e-45dc-ba44-022cbb7a7e88	host-sending-registration-request-must-match	true
ff20ae53-2245-40d8-8c88-b6b775b0f82c	e21652c3-e12f-41a1-b1ac-b915903efd7a	privateKey	MIIEpAIBAAKCAQEAlWcPPdeu+f0ThL6/X7aBYPIr70wa5/Vi3kGwklIFspJbAR8Ti+q4p5MFaXiAk1GTXA/6wGouwBgCAs/oLITHvvU16gdufGi2jmzxvq2ps91O+A4LuKKkiRh2IBDQMgTrZ3Gp4rjHc16MvVJeCK3EW5otWh8mdcoGnl2sh+ewAUhiqEsNqMRiczaevqZi6gXAopaRwbp1UhBWE4GkvEYwkd1EvvmskJtEHmEfA3dbGjptOYI27z4NhYaLlhX/O42cu6P4l/IzbeYmaIK8vAtTDtJh2xvd0VbVDfMO6EElkIfIS/aMm2yMY6S0Igm8UVPSsSvQctiSb/uITDHCslUZ/QIDAQABAoIBAAbU41bH18WxG5UglsYk5YYeFMG4uGs+ZoK0vML4ErAfbp9ewSlO9podCCySOMjjvEoDpgrp4BDjGCZyRNLSUILsGgbFR7lTHXPmSeiW7tFdb4i6tbD/6Oem8XHWavq6jFwaz7KhEee13Vgj3c5mSboJkwTRs1PEBOj1bhf50WvBhmlRsytLS1/9Egzt24ol3FfkfjPfW0wwsXF/FpmcpKTI03yR85IvQ1+V0R9KL9tWf5mpR6Eu0AhEdFnr1J2N2WFpHVND8zLucD4JPHB1WCSYPwf8HOQ1p5m5q8ZfkIxhUtPUe6nIs7uxlGErDAUaHbVpleh0RGlN/y/w46sA/kkCgYEA0di8vovypiKw2dCnb8YLxHnyb0EFocRXeZvqgqu6zh989voNusH4CDkanMDLeBdPKI6CEm0qMPlAXIvYjiWt9R13SnyYnXgkmWvz2RL0kdz4Af49vAePTFRHt6IXdoug9e4CFCZy5CCianSfiwxjTQgGal0te+Gi+U3Lrh4dAAUCgYEAtkMQQ5+REzuIpiewBNg2VtESfzOKVXYEaLLqeqfQG/SL4/pnTw8rcMmEYdULKrP0hVfTzZcpTm9mvgLFZNYxEjqBGnrVItDnh2+g8AfYPFHrJl9tyUJrDjEtowAVqHIxkrC0w8edas6Gf9vkjIi8FjhhUaoaJzqzUwI8gupma5kCgYAWIcXGUvxEiWQdlOQT5c9X6YSoNzNg3FRdfxQ8s9//3VJQug64o7yFIYCtB6huAgdXP7B4QQsyHxeqqxUr6M1wDKuIL5vo/8Lca4ZMXrNsruF5VFcufZ2fb0QhGfdfiVpwlamidmuno8l9cM9i2uueNDp4/VrOtf16Tbotov9sAQKBgQCFBf1g3Z0sxE6/tkepwYQnaQBvBlb4eOJO6AZq7v/c52cSRTgRY6j48JtGLA+yYxUcHTXg32qubHA4Th1zs0CtZTXE9dGJT58sIjT7+z2/CKrvnsaqFWe9O/dq+CzWfEHiJkZIffBOe91+hCcSytk7/npI+DWOXDF1bDRt+FY4QQKBgQDEJOuIlFxmn3391LP2gCCEh4pxrEGNQEvAYUqfm+hr+dYLOIMidvlUkIDsC2/tMZFpYYwoSjCmuM7VxGi74+t0hpjEp9ALXmuU0fEGzqGW5wsXmUl+u0iim2PaSvpoayHDxQzYQ9cK2ilkEKRc6a2cY0frwdExLGro9iJp2uPO6Q==
67406f58-3b8f-42a3-9042-c51aac611a37	e21652c3-e12f-41a1-b1ac-b915903efd7a	priority	100
118b7996-c06b-498e-88df-3c9986195900	e21652c3-e12f-41a1-b1ac-b915903efd7a	certificate	MIICrzCCAZcCBgGfGC6WkzANBgkqhkiG9w0BAQsFADAbMRkwFwYDVQQDDBBwbGF0Zm9ybWVfc2Nob29sMB4XDTI2MDYzMDEwNTcxNFoXDTM2MDYzMDEwNTg1NFowGzEZMBcGA1UEAwwQcGxhdGZvcm1lX3NjaG9vbDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJVnDz3Xrvn9E4S+v1+2gWDyK+9MGuf1Yt5BsJJSBbKSWwEfE4vquKeTBWl4gJNRk1wP+sBqLsAYAgLP6CyEx771NeoHbnxoto5s8b6tqbPdTvgOC7iipIkYdiAQ0DIE62dxqeK4x3NejL1SXgitxFuaLVofJnXKBp5drIfnsAFIYqhLDajEYnM2nr6mYuoFwKKWkcG6dVIQVhOBpLxGMJHdRL75rJCbRB5hHwN3Wxo6bTmCNu8+DYWGi5YV/zuNnLuj+JfyM23mJmiCvLwLUw7SYdsb3dFW1Q3zDuhBJZCHyEv2jJtsjGOktCIJvFFT0rEr0HLYkm/7iEwxwrJVGf0CAwEAATANBgkqhkiG9w0BAQsFAAOCAQEADF3lfggte1A6y9KRbQtcuXu/8MpZ0gLJ+iI/9SiaKspr9iYbI5pST8vTDBZnm1Ag1GqzuIA3hUGuvAsjhO4uzGgEpt2OIiXxhzfhS9ESAIuvFnQCfwpCuln+JoKZitMsOE2sP5gahf1f6yDTmuUQVEBRTBMabWvpUPiBeM+SVjsDS7YdC8WAU0i+P/uicXoye2OVZZvrrwDV8GgJyooxrXzhw8bCbdOh6F6uSUYLnDylYP/R2AB0zW7FPE9tIWSmB7rOZtocNQ0vYvLnGSwlYfwQw2360t8LLGGd2QPT7j6gTrY+RSuPdjRxGeVXVAaP1Od5QUHSXbUMJ992KtU1+w==
8668e468-b149-43f8-9a6a-1dc853b8fc84	e21652c3-e12f-41a1-b1ac-b915903efd7a	keyUse	ENC
17cdb274-d288-48ef-81c7-6a9ddd2cf96a	e21652c3-e12f-41a1-b1ac-b915903efd7a	algorithm	RSA-OAEP
406242e9-9ef1-4a4f-8116-f93235d419a3	bcc72ee5-48f0-4f3f-896c-1f64d3c8569a	max-clients	200
7655b851-422b-460c-8719-830add68034a	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	saml-user-property-mapper
5889ee17-f4c4-413e-a79f-7df23aab8d69	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	saml-role-list-mapper
d1456330-d374-49b7-a07d-0520e6e7b30b	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
a61e46d0-6c47-4af2-9d25-703b6ee6ba04	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
6d0ca9ba-8ea0-413a-a658-9d517c8008d0	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
73d7f577-0fde-4b02-ae72-8071d8c4d006	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	oidc-full-name-mapper
75d9b09e-a018-4904-9656-2c1e81e4f349	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	oidc-address-mapper
ae2c8b8d-b51b-46d1-abc5-8ca841c0b6a8	ab57c38e-d58d-4f77-a0c3-71d8514ab88b	allowed-protocol-mapper-types	saml-user-attribute-mapper
c2c8850d-d0ad-441f-b6b8-ceffeb21495d	2076fe25-fbf9-4148-9e5c-544920fdcad4	allow-default-scopes	true
69d0b04f-3fbd-4a98-9fba-7f851deef36b	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	oidc-address-mapper
9feaa6b1-d2eb-4b1d-bae1-badafeba82ae	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
c050fed2-793e-49c0-8576-ed22a8a8c61c	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
f0698578-9dd6-411f-818a-8a45a9d644fe	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	saml-user-property-mapper
22ed022b-c838-4d40-84f6-cda4e3855094	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	oidc-full-name-mapper
c097a92f-9743-48d8-ae95-47787fb0f57f	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	saml-user-attribute-mapper
bd3abf2b-c9d2-49d8-8278-7dda85f0695d	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
e17cdb43-aeda-4e84-98e5-ebc89774b535	b2d62117-5193-4470-b337-db309935b401	allowed-protocol-mapper-types	saml-role-list-mapper
d43e5c0a-f13d-4420-a38b-ffbab5f20c49	8cdafa32-1dec-4a4e-acaf-f10de2511f19	allow-default-scopes	true
df059ef1-1bcb-4d43-b3fe-3ae07f1172c1	4aa331e9-4455-4f72-9e63-4b2b38df59ab	client-uris-must-match	true
ed32430d-4343-445c-9e97-3bf6f39c3bce	4aa331e9-4455-4f72-9e63-4b2b38df59ab	host-sending-registration-request-must-match	true
00bb426b-09e0-45d2-afe2-714ddad6ca16	19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	algorithm	RSA-OAEP
c64af9dd-60f7-439e-9b9c-1d10c94250e1	19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	keyUse	ENC
de852065-f5ee-4ab3-8e46-d090ef23c751	b0203c07-4b2e-45dc-ba44-022cbb7a7e88	client-uris-must-match	true
77493358-9428-439b-8acb-a51ea13f936d	14977b85-874e-4670-9ed0-1726daf1c1c9	allow-default-scopes	true
43d30b8c-38f7-4242-9121-b07e0480259f	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
6a60f93c-e390-4fed-beb1-e42b0b4183d8	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	oidc-full-name-mapper
e4c34322-4693-47f9-802f-669f93075f67	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
ee2881ae-98c5-45f8-8e55-f2746a33cd12	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	saml-user-property-mapper
24ee622d-2927-4571-a702-0da991e27580	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
2f8a1023-88c4-4bfc-8fb8-ea8c19a5acd8	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	saml-role-list-mapper
332fee5c-6506-4fec-bd15-2109f4222561	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	saml-user-attribute-mapper
df2a5a95-0c7b-4b95-a404-c9841dd0221c	393014e9-4004-47de-a74f-9b52d554c92c	allowed-protocol-mapper-types	oidc-address-mapper
4e77d45d-623c-4d2e-a89d-4a5934ce02e6	19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	privateKey	MIIEowIBAAKCAQEAzvZuByS/KVQ5TKkPgEQUoNdeoUFZEouVx5nMbH/eE4hbIihcMnUsYBGK+cnWdSZQesua0tCU42WgnZ5LrYBZ7iQ6RKDLid2B50M4ZFV1Wm/8d+jnllqSUrO3/JZFmacHZu2a0UiOiSnNW0JNwv21kxRqdw+MceJ7wGRIpI3GwsG40d7Cc+J/EHwFDo2a+tW+DP7dYiVbWuxJxIaOzyVy0M+DCDxNsY28YV9ciAW345vghOgLEQTpLSGZFGWE8pRWmxB8ByJDLPEb6DWpyXR/3HohfvekdTYVGaRKsG45+unrPmgPw1IS2FP21B9semWYaGnECSmciCDa53HamNsDSwIDAQABAoIBABwuNTh8dMRljHvMv/3rceeeC8DnS1VEHmaUTDUwirDxgynj1vyj3yFMTOEgQ4xQuuqi000F9mVUOYoyEK8/kn84/ZofTuJR1VYDstJZSeZrkzP8nvQRFxVzFTKr1NmhgwItM5nFncA9fzyANbhpoBIL2Jynnaxay2ucpsDem9l5apxl7rOPK0OnpaJv7kP4jMegJbFGIPz4/mmID+hLzx9nMwSn35HtEo4l9C01So9v1FHHtDV3bjzC3wDqYBt4lb7jFDgKgWHrA50978LcXsXblvy05Yy4iFAyRxbAh9MyH1OMElRajF9Xhb6RMETWXEiQAMi7EeBqLkvbuxx8cfECgYEA6Ofpa5/BDFQyU9v6zx6Utt/fqkF7AIMFv0BFumdWhe+Rms5SkQItTyYMogy89fNw/nlIS4iKs5ICsReYOEcz2KFGEmRh31Lalq+o4CgDdxM8sPcwLkF36MMk1+rwJbU1KlOvCwkMluUQBrlWygHDpKwzrVXuhyzOGYU5QP8pcmMCgYEA43v5GCuG38EIkAqpfzMu546sAl2OOUkLq87bQy/2HD8vXCN0IUBAI59GnOZcuKkij5ulPMdrSmsOWb2XibitgMElnhe4LFaocJPWdSuM2lMTA8uXPWRnjec17EhKePR+FqLFoCBnQe15NWbxLNdSinjzC4T/+B6b10paIwmFi/kCgYBtif0vYABkcwkEhdaNKR3acKp6IbffAsBmNa1VwRphfc4mV9lfV/3FVujkIkeRCYDgPlxxIfPSBt77OVSfS2WoAhm5trqNVSN3cYBkhI00qLiHpopjhukFcRpX7qgpo/MwSBFwiD4CjhMehna6JWBFh7P6VwsLSlhYil7mIdv1bwKBgQDTjxcw0EIl06r38sKBZoNnnDsCFCnRwsoU5VLrIC6dRHHyGZLF3bAIsPcFsgNnRPy8W78HUFrKodd9xCGrVlIdnQM0DNEegxUxZXwcCf17iaj0baHg2YCo6u8uKGkCJjchLA3pBlmhaY3BCVSgJDxCRYubGfzHrwe16BL95LwLWQKBgDWeysOPYKvXLk8E38kjPQ+UgLmenG5Do4vzwfJD1BWkjz122OzbsyIPWui7LJM5BeJ3jhn8P1aWsBXftD/b1Ve2m/776KlyaDpcZiZVc1Bn3/fwHFbxszE3EqqoQGrEehRmfZ4mYQg0Hk2zStSBevv60dTRTq6BGgd9lPuToOwy
1ab29816-f52f-4dd5-93d8-352f093cab1f	19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	certificate	MIICpTCCAY0CBgGfGEkAQzANBgkqhkiG9w0BAQsFADAWMRQwEgYDVQQDDAtzbWFydHNjaG9vbDAeFw0yNjA2MzAxMTI2MDVaFw0zNjA2MzAxMTI3NDVaMBYxFDASBgNVBAMMC3NtYXJ0c2Nob29sMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzvZuByS/KVQ5TKkPgEQUoNdeoUFZEouVx5nMbH/eE4hbIihcMnUsYBGK+cnWdSZQesua0tCU42WgnZ5LrYBZ7iQ6RKDLid2B50M4ZFV1Wm/8d+jnllqSUrO3/JZFmacHZu2a0UiOiSnNW0JNwv21kxRqdw+MceJ7wGRIpI3GwsG40d7Cc+J/EHwFDo2a+tW+DP7dYiVbWuxJxIaOzyVy0M+DCDxNsY28YV9ciAW345vghOgLEQTpLSGZFGWE8pRWmxB8ByJDLPEb6DWpyXR/3HohfvekdTYVGaRKsG45+unrPmgPw1IS2FP21B9semWYaGnECSmciCDa53HamNsDSwIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQCvkUyndhcgowIaZ4NcYQSl6M4FzkLxo9tPyEtq8qFpNNliKr3yC4tCUD5Vxh/PgLbGgfb4hU2lVQeC27Jq52mvEujLw0rljJ+y3G35KiUDUAcW6KMqiEJMtTCfC49PDg6u6fGaXCbq39CJOLMh1F/mRYYgHpwabQn+JN1OdCVF4/T1zcGp2Haso4BPqUTkQxvGgGmN2x+0HJw4qKC7d3JeF6YbkZq4oOlhuC2pThpXeGdlIv7O9rou6UlVAG9W9cRnBw+GCgz6jVpkTAIxvSX7zncrbJyDvDxp9p1z6PvzxIyo7EbN8XIMlQpDAKPnTrOnfM0wPIoUbnwHF4mPvPpJ
5174c173-89c4-451a-86cf-70db94fd0ed6	19c43a6d-960a-4dc3-9e7c-5ef89219b1d7	priority	100
7f04391b-7a44-416c-8292-9ceb46369d69	ba4ace38-8d9a-491a-8aa3-2597ccee9795	priority	100
ed778673-d548-4f11-b092-81819c47d024	ba4ace38-8d9a-491a-8aa3-2597ccee9795	algorithm	HS512
ee25758f-7e8c-45c8-a49c-e6e85bb7fdf8	ba4ace38-8d9a-491a-8aa3-2597ccee9795	kid	6ebd364b-9e8f-4dcd-a350-10f398bae08d
fcba0163-1bea-46d7-96b5-643fe40d32e5	ba4ace38-8d9a-491a-8aa3-2597ccee9795	secret	GQtMRNB9G1EXeiQOtG7vXenYQnMIyghh-ZWy41H-PwCwXnUnV71u8xt7z9QJZZ_GSkOzvCrr5uSdPb5QMd72j_6TTG0KLDmrrztl43_KoRYMjQYouFTu9siO6o7HugVU70-bAvJdxLvIL1RTYWzb-iBLIn6ysddhdgQmTEQNquU
d14c0558-904d-4e13-a69c-e5c2434c93e8	33840fc2-dad6-4458-b0a8-aaa657c130f3	keyUse	SIG
9bf78630-4d22-419a-b1a7-766229cbc7b3	33840fc2-dad6-4458-b0a8-aaa657c130f3	privateKey	MIIEowIBAAKCAQEAlLszURYM284MyQpMg2TxrhT05qE393wWekyc/SHi3N9+BvQYAVr2wo8ggYHEHY8gb+mx8BMVAkVDQeIJMAaYYocpICU8LKt6v3NQoQHuOfMD217OiMLc+m3fsP04Pwf2NXKbfE5Z5RoFjBHQ2jUHjWJY6luSjtn6Jz74qLjWiczaXu+5AhG8HRX4PVtIreQOeacPbGKyhWIhHIZNPCpD7Do33/7vb44J5UpNcH20ZzCMAT+iRufFARB2RtpsCi6w6BsRmfe+Xd6MfjZmolszWURigKRaTG4H5d9XGHjGtIu+2cgAromt6ZSq7X/SuCX21NWgiHMVUp39KDLl6u4hKQIDAQABAoIBACSt0v6/B55Ify4wQETSJukFyYy7+xupSjjQfpHNos9SPZgU5/ujlYHPPta5bCZXBwqacTOx3QdBkg/kINxrb/ayewGutRuWEZA37c07NucrJ4sY4ynmlo0a/iS9mfqvabhuImrCPtipdW9A8Ibzt4BWXfOz8B2TI6Y8mSGw7dWvI0U5x6OCZ7xxGXRAFVWBcrqHGMQqGQym8WnL+06kXjsdwC90RI51xJX99YA3clK89Hi8ilXBfDiul9SIjzZ9An3airULauTxZfccOXaNjIqaZPSKm4rzZyHa+6akwNVAvpSl/AGCcf6LKVhzsm2iiSRc/Hv3xn36QJzZHSdaSsUCgYEA0BECFuE59zrfaWIIzaWPsKQzVOtjMSabrDZsiCC7VHznSAER3qjJ2JO76+RyV3aueGC7yZwLwlHwVURqADs7/SOSufTqBJkSCi/Zxd4doA7kREEFy+cdSygpwqmfEZUZIyqJm5cpjYqWq3xZF4Ll2ti/3Y90fnWJca2+v0Jypc0CgYEAtv7SVxq9crQVYlSltAyGx5HoZZCnSt7xkgUKZGjlojbUQ8PJN4Yb3emGwSaYEEktL6fdQzHGMCoJvmZDvgSZtYAVCeROYw3no6zymBUKfOcyvcqrFnqWqbCq7oN1fv/k+JmcLWp+T/HfSn27Je2xUR+bU1x2MGscr1I96r0AzM0CgYEAvudMFngSOk/PGp7zWtRUUYtm+bx7p2Y4ITtSO1hk6PEtN7ibj8MoZ8ms+5QAgVWIYTTIOAQwGdwaefGqnIm80dco0evwJ8XWcnhTBKR2ZBwlvqY2Tk7AZuvz3QLKy1QU21o1Jgf8/TwyYCdYiyT0Bucmq3/rL/hh4ma7BDtQe+UCgYAyXouZDDQvOWBVc4U0vOnwTroQErPpiNonXhnRL4pdjwf4KxDcpp5PA7NiwPPEL9AvON5PW4j4wulxrgZhbDS+36Vq1pu46h/jTnxr3gbIAH9X/EwBYPbNeGJkSmzx6w+kWevCGHjyGlpiz57OvtE6yh9f36hshV8c5t9CGIn61QKBgCgNDaEc2dF5rCGnWR+NoqIXzgrJNDYMtmw0T1/GWQSLIXTHOmbxb+SgneMO3rH4xgvBf1U9T1355YIJjpIUGPbQPq0KtNrbxncLjXbzQPR5N9k1Xddgox+mvQPpdZsxpXivJNREy3cbS1MFxQX9U7emJ5L8R+K8Jkw1fpkTkIeb
310e669d-d995-4140-a3ca-94b4f999c469	33840fc2-dad6-4458-b0a8-aaa657c130f3	priority	100
f7fd1a32-65f8-4fc9-a28c-5c7609f045db	33840fc2-dad6-4458-b0a8-aaa657c130f3	certificate	MIICpTCCAY0CBgGfGEj/kDANBgkqhkiG9w0BAQsFADAWMRQwEgYDVQQDDAtzbWFydHNjaG9vbDAeFw0yNjA2MzAxMTI2MDVaFw0zNjA2MzAxMTI3NDVaMBYxFDASBgNVBAMMC3NtYXJ0c2Nob29sMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlLszURYM284MyQpMg2TxrhT05qE393wWekyc/SHi3N9+BvQYAVr2wo8ggYHEHY8gb+mx8BMVAkVDQeIJMAaYYocpICU8LKt6v3NQoQHuOfMD217OiMLc+m3fsP04Pwf2NXKbfE5Z5RoFjBHQ2jUHjWJY6luSjtn6Jz74qLjWiczaXu+5AhG8HRX4PVtIreQOeacPbGKyhWIhHIZNPCpD7Do33/7vb44J5UpNcH20ZzCMAT+iRufFARB2RtpsCi6w6BsRmfe+Xd6MfjZmolszWURigKRaTG4H5d9XGHjGtIu+2cgAromt6ZSq7X/SuCX21NWgiHMVUp39KDLl6u4hKQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQAjgtPUr+CK1yOWlRF64S2C9nuSylg2nfHSgl2AseYps6q8o2tL2+1oIVf/tOGQcBEYHL6OhkL/y0gqrboALksrefVocWLLA2vHUCnlW3DeHOfUCnAuC5F8+koE28yyFDUvqmKFp3K1ZjDoP4uKtUNqBigGicD+a3bMHiCrSWcDt0E4G7zWuaAAJTHEakN1LJBd09njc5XvE4AmjCwYaOZx5AA5NCSNMx3RgEDF636PhuRERbXs5kA4R6vrwg5ZZzZ15CmG1GufmwrDYaP0p+6onuyaf6fVNh3dN1a059CGzqoNW2OKAYGHy8WkwQdUkpwiLOWREmdtjYffoMPf7Nvi
aee60802-d357-4e73-abb0-77a140d22464	3dd90a25-ad3f-4a34-8281-99760176a67b	priority	100
3b0e49a9-2fce-4915-b5ad-9138a17381a2	3dd90a25-ad3f-4a34-8281-99760176a67b	secret	RkbsyJpG-KaoLBR-bignwg
9809f8e8-a3e4-4ed1-93bd-0aaebdce06b0	3dd90a25-ad3f-4a34-8281-99760176a67b	kid	c0b934c1-71ae-4746-a4f0-fd6eb2471234
8e7ec0b2-b085-49f3-844e-f1356dfee71f	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	oidc-usermodel-attribute-mapper
45934fa9-e4ea-42db-8d16-e5e57e391eb9	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	saml-user-property-mapper
d4478eb6-7ae5-4627-9178-5655c8564037	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	oidc-usermodel-property-mapper
f73112dd-e7ce-407a-b432-419007928479	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	oidc-full-name-mapper
d0a0d5d2-3cfa-4ae1-8cd6-a566fdee369f	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	oidc-sha256-pairwise-sub-mapper
b85e33ed-7afa-46ed-8120-1ed1c6c5cbe9	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	saml-user-attribute-mapper
89ac044d-deb8-4c1f-9cc8-3b10cf1b125a	cd69696c-24b6-4406-875d-4bc00ef842b3	allowed-protocol-mapper-types	oidc-address-mapper
80b125a2-c470-4502-82ae-a8163a1b678b	86094bde-109d-4773-90e2-8c87b7ee1ebf	kc.user.profile.config	{"attributes":[{"name":"username","displayName":"${username}","validations":{"length":{"min":3,"max":255},"username-prohibited-characters":{},"up-username-not-idn-homograph":{}},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"email","displayName":"${email}","validations":{"email":{},"length":{"max":255}},"required":{"roles":["user"]},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"firstName","displayName":"${firstName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"required":{"roles":["user"]},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"lastName","displayName":"${lastName}","validations":{"length":{"max":255},"person-name-prohibited-characters":{}},"required":{"roles":["user"]},"permissions":{"view":["admin","user"],"edit":["admin","user"]},"multivalued":false},{"name":"tenant_id","displayName":"Tenant ID","annotations":{},"permissions":{"view":["admin"],"edit":["admin"]},"multivalued":false},{"name":"must_change_password","displayName":"Must Change Password","annotations":{},"permissions":{"view":["admin"],"edit":["admin"]},"multivalued":false}],"groups":[{"name":"user-metadata","displayHeader":"User metadata","displayDescription":"Attributes, which refer to user metadata"}]}
\.


--
-- Data for Name: composite_role; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.composite_role (composite, child_role) FROM stdin;
5650b984-c6a8-4360-86c3-164318770281	2171ed3f-e670-4b4a-838b-432d5d873e26
5650b984-c6a8-4360-86c3-164318770281	d4d43531-81e6-4f6a-97f3-87e529d36d12
5650b984-c6a8-4360-86c3-164318770281	7be257ff-ab7c-48d2-aeea-0fc0dbfc981d
5650b984-c6a8-4360-86c3-164318770281	93c49ab4-3529-46eb-bdb6-f6d7de94afa5
5650b984-c6a8-4360-86c3-164318770281	4b7dbc6d-b751-4087-901a-9730b7b781d0
5650b984-c6a8-4360-86c3-164318770281	6e6cca0d-6f05-41a3-be63-6ca55c2e12b7
5650b984-c6a8-4360-86c3-164318770281	e331969c-350d-4e69-9a2c-300c46d0a298
5650b984-c6a8-4360-86c3-164318770281	8c32b201-e8a4-43d3-955b-eebf8738535d
5650b984-c6a8-4360-86c3-164318770281	63483b8f-cccd-4b48-8ed2-aa786beff4bb
5650b984-c6a8-4360-86c3-164318770281	66dfd9a8-b177-4a29-b4af-4d213318331c
5650b984-c6a8-4360-86c3-164318770281	d65b7342-c9da-43bb-b22b-37030678614f
5650b984-c6a8-4360-86c3-164318770281	254ccdc9-7490-431e-b329-46cd5c1f79c7
5650b984-c6a8-4360-86c3-164318770281	5079921a-17af-4795-b6f4-46106425f2ef
5650b984-c6a8-4360-86c3-164318770281	c447f728-708d-4b79-8763-6cd9d3c449a1
5650b984-c6a8-4360-86c3-164318770281	3f7388aa-f7c3-443c-9a00-b24ebda34da7
5650b984-c6a8-4360-86c3-164318770281	ba4f0957-67fe-408d-9ad9-6dd369d8f3ca
5650b984-c6a8-4360-86c3-164318770281	feba3ab5-6a23-4de9-9429-74dbb268f3b1
5650b984-c6a8-4360-86c3-164318770281	4fed1692-8eb5-4dcf-a4e7-cbf4e3c1b9a5
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	55dec5cc-99e7-4f1d-8860-7e3d2cc835b7
4b7dbc6d-b751-4087-901a-9730b7b781d0	ba4f0957-67fe-408d-9ad9-6dd369d8f3ca
93c49ab4-3529-46eb-bdb6-f6d7de94afa5	3f7388aa-f7c3-443c-9a00-b24ebda34da7
93c49ab4-3529-46eb-bdb6-f6d7de94afa5	4fed1692-8eb5-4dcf-a4e7-cbf4e3c1b9a5
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	342d9096-b03f-4326-b1b4-b137b8d2e348
342d9096-b03f-4326-b1b4-b137b8d2e348	50ce9486-0143-44ba-8787-689bda4e765e
73511c25-9e15-4e76-8388-280092428538	67accf88-2c27-4424-b20b-7baf94be3278
5650b984-c6a8-4360-86c3-164318770281	9949b2e8-b041-4b01-98c5-9285cc98c11d
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	ae38ddb2-41d2-482e-85f2-edc9118bf2c2
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	540cf173-5ebb-4d53-9f7a-1ea291dc6a22
5650b984-c6a8-4360-86c3-164318770281	2ce38629-16bb-4bb4-9182-5272b40bb885
5650b984-c6a8-4360-86c3-164318770281	c3965494-5dbc-4b1c-9be1-c41fc9122f73
5650b984-c6a8-4360-86c3-164318770281	54acb520-38ee-4ab6-95a3-7a730a39afd0
5650b984-c6a8-4360-86c3-164318770281	53511ecc-78ca-4e25-92a8-9484f673d0e2
5650b984-c6a8-4360-86c3-164318770281	a95e9ec9-c392-4f87-9768-9976a3a92596
5650b984-c6a8-4360-86c3-164318770281	42cad862-cc0e-4e3c-b7a2-bed6a9a3378c
5650b984-c6a8-4360-86c3-164318770281	1bc2dc23-f71d-4509-978c-eb450b459262
5650b984-c6a8-4360-86c3-164318770281	5f3789ef-1c67-4a32-8b7c-95d1583f4ac2
5650b984-c6a8-4360-86c3-164318770281	a0c4235c-5496-49bd-9c50-2f474626aeee
5650b984-c6a8-4360-86c3-164318770281	127d827c-262f-406b-9870-b06fb6113280
5650b984-c6a8-4360-86c3-164318770281	9bb9ad58-4cdf-4ff4-aa7e-224b18234141
5650b984-c6a8-4360-86c3-164318770281	c879f248-26b8-421a-a723-e9c237041a3e
5650b984-c6a8-4360-86c3-164318770281	2813bcc4-715b-44e8-95af-436a1250ccd5
5650b984-c6a8-4360-86c3-164318770281	c3aa9463-41dd-4248-966a-699f3ec33d49
5650b984-c6a8-4360-86c3-164318770281	e3b24820-3ea8-47c6-b458-9784217700a7
5650b984-c6a8-4360-86c3-164318770281	b32263a1-b987-4b1a-8a1b-d5bd10efb8f2
5650b984-c6a8-4360-86c3-164318770281	36a8c13a-0054-4059-9460-cb3d09dbb672
53511ecc-78ca-4e25-92a8-9484f673d0e2	e3b24820-3ea8-47c6-b458-9784217700a7
54acb520-38ee-4ab6-95a3-7a730a39afd0	36a8c13a-0054-4059-9460-cb3d09dbb672
54acb520-38ee-4ab6-95a3-7a730a39afd0	c3aa9463-41dd-4248-966a-699f3ec33d49
998d8f55-103c-4486-b705-871e773b55e1	9fd49d77-d3ff-4cb4-b6b3-5b2f13d76036
998d8f55-103c-4486-b705-871e773b55e1	6557c8bf-04f1-4220-9281-198c9329f169
998d8f55-103c-4486-b705-871e773b55e1	869e58f7-8fe9-4381-bfd0-698985e90aa2
998d8f55-103c-4486-b705-871e773b55e1	20589b20-a192-4d45-b3ef-41fd67fd7295
998d8f55-103c-4486-b705-871e773b55e1	94b473fd-d393-44c2-8b7f-366727a2a820
998d8f55-103c-4486-b705-871e773b55e1	5f4ee40b-493a-4f4c-9e53-ff1a62f8a85a
998d8f55-103c-4486-b705-871e773b55e1	d6bd889a-4d60-4229-aa9d-786e4a21f40c
998d8f55-103c-4486-b705-871e773b55e1	894a6f13-677e-4652-91f9-bd966c7ce297
998d8f55-103c-4486-b705-871e773b55e1	8645a0c6-bac5-406b-a652-c11ed62d3218
998d8f55-103c-4486-b705-871e773b55e1	3278fe4e-d205-45b6-bdb8-6c3ec40b8923
998d8f55-103c-4486-b705-871e773b55e1	3fd8f936-4c6c-4d21-aa83-3b0073974531
998d8f55-103c-4486-b705-871e773b55e1	f365ae32-689e-48f4-a98e-255ffbd2af42
998d8f55-103c-4486-b705-871e773b55e1	2edd6133-604d-4155-b2ae-890e0d6f2860
998d8f55-103c-4486-b705-871e773b55e1	0b4f93c2-795f-4b0f-b20d-0c4e2ec7c201
998d8f55-103c-4486-b705-871e773b55e1	789eb3b5-459a-4ac4-8f83-f48baf602a8a
998d8f55-103c-4486-b705-871e773b55e1	902b2b93-12bf-4e8f-bc9d-d0c7c2bb3d64
998d8f55-103c-4486-b705-871e773b55e1	0756779c-6ef0-42e3-bc70-0b2f8f718501
20589b20-a192-4d45-b3ef-41fd67fd7295	789eb3b5-459a-4ac4-8f83-f48baf602a8a
3209610d-c3e0-478f-9e69-5078e51fa2e2	85bb459f-b034-4640-9f9b-9c39572487ae
869e58f7-8fe9-4381-bfd0-698985e90aa2	0b4f93c2-795f-4b0f-b20d-0c4e2ec7c201
869e58f7-8fe9-4381-bfd0-698985e90aa2	0756779c-6ef0-42e3-bc70-0b2f8f718501
3209610d-c3e0-478f-9e69-5078e51fa2e2	ddc79483-a4aa-4928-b496-02a84d6ada27
ddc79483-a4aa-4928-b496-02a84d6ada27	dfdc1644-88f1-48cb-b39b-ae10abfa442b
9ae92762-5b92-4b52-a12f-d07905dc6c29	5cab47e8-934e-45f7-be05-2aa4232594e7
5650b984-c6a8-4360-86c3-164318770281	c0924d47-3f07-4732-a30e-6e61cf702b20
998d8f55-103c-4486-b705-871e773b55e1	0c700f4d-ab68-4735-8d42-8baeb8c2e925
3209610d-c3e0-478f-9e69-5078e51fa2e2	1bb8eaf2-9cfb-445a-95bc-a5d892057ed7
3209610d-c3e0-478f-9e69-5078e51fa2e2	5806c6da-b084-4503-aec9-d4a5ce4f08a5
5650b984-c6a8-4360-86c3-164318770281	2d07fbd8-cbb3-4075-aa70-4a607e7938dc
5650b984-c6a8-4360-86c3-164318770281	ee875c0a-4272-4b47-93e2-44277db3e64f
5650b984-c6a8-4360-86c3-164318770281	51003cb2-1e24-402a-9b2c-cdd1afacec35
5650b984-c6a8-4360-86c3-164318770281	944652cf-b72b-471b-a121-03167a58bf31
5650b984-c6a8-4360-86c3-164318770281	17b4a453-0fea-4632-9b0f-36b404f32816
5650b984-c6a8-4360-86c3-164318770281	67c63331-f681-475f-aa62-179f3fc777d0
5650b984-c6a8-4360-86c3-164318770281	bc7949b5-55de-4706-9f9c-580688005428
5650b984-c6a8-4360-86c3-164318770281	8f18f309-f30a-4d86-8a0b-05a6110b93df
5650b984-c6a8-4360-86c3-164318770281	a69c37fc-c410-4232-83d0-cd125048d689
5650b984-c6a8-4360-86c3-164318770281	9e47ac0e-1eda-44e2-837a-b1f35e4aada6
5650b984-c6a8-4360-86c3-164318770281	3b5c70c8-b7f5-4d3a-8021-d99be2422489
5650b984-c6a8-4360-86c3-164318770281	fa1169ad-28ff-4440-b1a3-ae08e46fd24c
5650b984-c6a8-4360-86c3-164318770281	3f0fc970-efc5-48f5-af6d-193a6f259a96
5650b984-c6a8-4360-86c3-164318770281	5177f0e2-12ba-46d3-a4c7-463372f118fd
5650b984-c6a8-4360-86c3-164318770281	b4dc668a-ec85-4800-a7bc-36457038a11f
5650b984-c6a8-4360-86c3-164318770281	060c04fc-c720-46d4-a101-bdd75cbced63
5650b984-c6a8-4360-86c3-164318770281	28cff196-e832-4605-be64-f45cdbef9602
51003cb2-1e24-402a-9b2c-cdd1afacec35	5177f0e2-12ba-46d3-a4c7-463372f118fd
51003cb2-1e24-402a-9b2c-cdd1afacec35	28cff196-e832-4605-be64-f45cdbef9602
944652cf-b72b-471b-a121-03167a58bf31	b4dc668a-ec85-4800-a7bc-36457038a11f
38b1e947-d49e-4967-b256-8ac381fc1148	7d7239de-ea60-4394-8da8-cf567e212bf3
38b1e947-d49e-4967-b256-8ac381fc1148	49cf1aad-74e2-4050-9624-3986de286933
38b1e947-d49e-4967-b256-8ac381fc1148	aaf983d6-da6a-4032-a445-7155a42ab9c8
38b1e947-d49e-4967-b256-8ac381fc1148	e720a5dc-4c21-4769-9356-85cdede9aa24
38b1e947-d49e-4967-b256-8ac381fc1148	03bbb552-e779-4551-a135-58a183c4c62e
38b1e947-d49e-4967-b256-8ac381fc1148	d705c775-55ba-4631-8547-d815a1f71b40
38b1e947-d49e-4967-b256-8ac381fc1148	a4a23a85-c976-41ed-971e-3f49d302a6da
38b1e947-d49e-4967-b256-8ac381fc1148	99df3929-ad70-4232-93ce-34b18da7885a
38b1e947-d49e-4967-b256-8ac381fc1148	fe043124-aa91-4aa0-9746-dc1c8c425535
38b1e947-d49e-4967-b256-8ac381fc1148	8d17fb1a-6f0d-479e-9819-39e6f5fe813b
38b1e947-d49e-4967-b256-8ac381fc1148	1f4c8819-29f4-483f-8dc9-fe3d771f9b52
38b1e947-d49e-4967-b256-8ac381fc1148	9e16f1b0-e64c-48fa-a434-f0f650b967bb
38b1e947-d49e-4967-b256-8ac381fc1148	04e14c7b-b467-4470-a787-76c69b7f0422
38b1e947-d49e-4967-b256-8ac381fc1148	71b2ddf7-0195-4f4a-88ec-d4dc4d34ec62
38b1e947-d49e-4967-b256-8ac381fc1148	df6e1469-c470-4bdc-b03c-1d3907762370
38b1e947-d49e-4967-b256-8ac381fc1148	7fc45597-87c7-439e-8db5-f69addde70e6
38b1e947-d49e-4967-b256-8ac381fc1148	0b9d741d-26f4-472b-9961-cd88381531a0
aaf983d6-da6a-4032-a445-7155a42ab9c8	71b2ddf7-0195-4f4a-88ec-d4dc4d34ec62
aaf983d6-da6a-4032-a445-7155a42ab9c8	0b9d741d-26f4-472b-9961-cd88381531a0
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	2fa03de9-69c3-4a5a-a726-9f4ea5b2e086
e720a5dc-4c21-4769-9356-85cdede9aa24	df6e1469-c470-4bdc-b03c-1d3907762370
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	5f0fb361-0dea-4069-9142-70491c0ed68f
5f0fb361-0dea-4069-9142-70491c0ed68f	94f2216e-bf26-4a8b-86d3-3d0c317238c2
0c3d8eb5-2d3a-474c-8e4e-cdaf52cc3d3c	5d2c748b-955e-4b7f-94a3-a01bea39f538
5650b984-c6a8-4360-86c3-164318770281	24b21e80-6e97-4339-90bd-b4e23a094176
38b1e947-d49e-4967-b256-8ac381fc1148	59251041-9187-4239-9fc8-37a575a82b48
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	62846123-b3ae-4585-9974-8f1b618cc069
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	702023f8-f89c-4458-afc1-3d67540b89d6
\.


--
-- Data for Name: credential; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.credential (id, salt, type, user_id, created_date, user_label, secret_data, credential_data, priority) FROM stdin;
f402a750-5c4a-4f4a-961c-58bd8999618c	\N	password	a411426c-8d55-462b-9a76-04c5ccabd118	1782731436702	\N	{"value":"+6L2uh9od4DGZaWOUe+eaS1lPU9dovzch3zJ92BYrhE=","salt":"EqO++pZC0rAvdXOYTM7UKg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
5d980b3a-a9ea-40a3-8f0b-2588658921fe	\N	password	8d240ffe-9a19-41e2-b5c6-e048b2b2ded4	1782818389182	My password	{"value":"kPhw310iTUhIHb8EuOs9xTDR3LrVNh8V+pu3a9EtIAk=","salt":"YkKyeBC9PEucYSu55pUqDw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
fc8c8a46-d11c-4f15-8f32-788e1eff9a9f	\N	password	a27397a0-9714-4251-a9cb-fc6cdadd1c14	1784025597552	\N	{"value":"jHvfULLpGyGiZhT1Rg4TLkWUG53/eBEs7KAhhBMKlLI=","salt":"UOQamx+oiYqFZ96g5Ptsqg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
e2778c50-5881-4eb4-9615-8fbd09d9995b	\N	password	ec07d9dc-1d34-459a-afce-b20256b34bae	1784026316587	\N	{"value":"6QCs1wLcwO2xRrO5vT0k/9q0vupEjT0C5Yp0yqQX5GA=","salt":"iYVyFcNt4CMWIOyL6CqEDg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
be5e955f-241c-44ed-9f54-d2458e53c0b3	\N	password	b03867b6-332e-4a47-99cf-41f77263429e	1782994092507	\N	{"value":"gSkgQW9TsLsjjJEAWq00S061+BzdDzFyeYsKVoTiyFQ=","salt":"/qDRKip4mJjqe5QqAqp9+g==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
6b5cb04c-e12c-4d6a-a3de-b14a4a89cc5b	\N	password	c597c98c-3ca9-4af7-a1cf-28a178a5d9f5	1782994338920	\N	{"value":"lUHoaLK8cufNDodZjxCYcArnX2LaSs3Nw+9elf8tGSE=","salt":"zGQRJoHrr52mZ5FOeJhLBg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
f53a57c9-63e4-4d76-b3af-ad2ad46b5aab	\N	password	fc4af818-eaaa-493d-9c30-e0fda104ca78	1782994647970	\N	{"value":"vUClC8nq3v+v3QWYaeUUxC+2KIfPpu3sYyoK1VIFsvw=","salt":"59h6JfLWkzvF+8Y0cfkagA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
83d9f3a7-0f81-40de-951d-8f983a441379	\N	password	a07d36f8-7670-437e-a779-94ea5d03cbfa	1784112800071	\N	{"value":"7xUIADDoPTSyMkYxdX8mj0oSGi6DVpyM1CPG58R5/j0=","salt":"2t3uZtQqAHuvOEGGJgvFbw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
5b47ac97-9e0f-49f1-9c3a-5feca96b95f9	\N	password	7b907c28-59f0-4426-90f7-987972d93ee6	1784362776706	\N	{"value":"QswYNd772JGVi3iL2qSf4L/Xa03+UYHHBuV6LYW09Eo=","salt":"oVJHShSujC9yRU3GmW5XGA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
6a7e6c4e-12ab-4325-8a06-c25de50bdfce	\N	password	0f20480f-07ae-41b1-9c2a-eab9d4343ec6	1783847423598	\N	{"value":"+WJsWOmu//h6U//PjDIKHzNjlXsyQWsEDXVABM9PoWM=","salt":"l69sH7aPcXmwTeLw2spYLg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
af7c9231-7acc-4cf9-8cde-e0815ea34ba6	\N	password	b036bf2e-b3bd-4ee4-b23b-c030512ef85f	1784391144765	\N	{"value":"pe9fOzp8etljzuiNXv9zND81NP83E2SJTjqlMtdczD0=","salt":"86vTrq0owA9SpwDU5RJtkg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
8e2ba000-cb48-402c-9791-f764426db675	\N	password	bcd0a536-7bb1-4203-b198-cc62e64e04e2	1784391201949	\N	{"value":"lwF8Sbg/EPK8FJSNeDRjg8fe2/IECfoPVDsJbbdMASg=","salt":"7zCqphBhN+zySTYuKTBMZQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
492f2314-51ec-4e34-9f4e-04816dab2f48	\N	password	bd84ff87-df12-4e7c-92f1-81172429c43f	1784454905252	\N	{"value":"UTsRCCwKQDG0MpsjTJCAWOtX7QCxZ1OZmCBSyagysTk=","salt":"62eEwgK10ggSEo3+PoRnvA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
b01f689e-faae-44aa-a556-5b2643a10093	\N	password	8e539a09-5348-4db0-8ee2-3308ad684eb0	1784455271162	\N	{"value":"Tl5HNc6+6s4DujazQJeOQOYm8s0w8arYDRWydWz8EcA=","salt":"sZUbgyBeGrvOzbccXYsTuA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
688f73d5-d7ed-4095-9f86-ffbea5e7b741	\N	password	5291b7d6-21a1-418a-8814-1f01c9f5a945	1784455300158	\N	{"value":"L37gCb8VjNRnlbcV3vTo3Yg4O9Xj/T7Jh9O5B7TO5A8=","salt":"aH9c0HEtSPbDX1GJ5OWOsA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
c36ce449-3e8e-48e1-a3cb-84d6971a4c9a	\N	password	e35952af-e25b-4aa6-9ab4-61ea47c53dfc	1784989718800	\N	{"value":"VDQ0ucixZ7TKDQOQHhb4PuWcbYQv1/Eu7fAxJ3gRpiU=","salt":"iKo/bZ6/cf4jN0VGIPqVbg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
a7e6d04c-9333-45d7-bb30-c98bb8604b84	\N	password	9f7dbe19-063f-4a00-aa72-6dafa5922eed	1784990839662	\N	{"value":"tlhrZBvuyRay6IWmIw1jL1GSC9CzifCBge5bQARtvnU=","salt":"4aZfhITNA6Y3EkMpG/tXFg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
1ed7de2f-abc5-477b-ba29-1d192621de56	\N	password	b0afe140-0b06-4065-9e40-bffe43e90b88	1785197429762	\N	{"value":"j56cx6Tr49Sib5g5BqV8vRoPy7oeNaN4PvwJJ15Ylo4=","salt":"dRwYbaCMVAS/LQwnbnJj7g==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
f484a594-dcc9-46eb-b5cd-5c9df3f5778f	\N	password	0db58312-8a2b-4f6d-b418-269a52584d94	1784991000204	\N	{"value":"oUD7Zb/AqHIQzOTzR0Q7A3G/4ddyzi0zkxHdIS5W9ZM=","salt":"GlljwwQlQC1sccqJrD6p1g==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
640c1e37-113f-4ab6-9e4c-801ad11318a9	\N	password	1851bee9-fc26-4e43-82f7-59d50d4d7b45	1784991164722	\N	{"value":"ksQA4yj9J44Zjmg0tpDvD+fM0iNyJHdCa/Ej5UZiPzI=","salt":"EJTUk3DfllZlqjG5LsXwQw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
65df8380-5b77-4454-8d70-1914b047ad28	\N	password	79eda9a9-c697-476e-be1d-03aaad8f7297	1785109594163	\N	{"value":"qsiBeF4MEu9w3lARa2RhP90w7z6CWyAaFZceL1swFYQ=","salt":"hXaje6Y+2Cmh3PxlFjbCBQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
1212fee2-5cb2-4229-a0c2-a8a11672f07c	\N	password	fc05c939-7e24-4cfc-90b5-3ecd74572b39	1785195164810	\N	{"value":"aNEDN9abYjdAs5WbyMKLibe3rqmLT4ul4qGA3u/M6gw=","salt":"8FHnMx3Hn2qG9HkRQL0ncg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
fe0eb284-b2e8-4bd5-a916-bba7c80c61db	\N	password	41e4bdb8-f0dc-4351-9c33-54fd227f69bf	1785537857909	\N	{"value":"DUpgc3KFCjQU1wjBTeu2QsYtKM3JlLCH1kCrSt74TBI=","salt":"86hWWaQ4N3Qsco7uX6TuNQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
aa73e9bc-1ef4-4900-8f46-9db625bc1701	\N	password	fce143b9-3164-4e97-b49f-b7a39e448e35	1785709307888	\N	{"value":"mFc0dc+qEFda8+LodtlhBYrrC+B8VTRxxue0RIdt+KU=","salt":"ADl61F7QXOjwRDb65bUwPA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
6ebfceda-743a-42e8-b70c-24e2abbeb32c	\N	password	11faae6a-09c9-4778-b864-0b015407cb8c	1785365738117	\N	{"value":"2q0M6JRaGljBNSY3F+fX0BeKjpog/XHa/8110ct+IcA=","salt":"2yM4X+vJ/lYKDlf6653dAw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
107e377d-6b0b-47d3-b5d8-b51eb5f98f5c	\N	password	7d5d0a38-2de6-460c-a14b-e735442f710d	1785366399498	\N	{"value":"QsIZ9segrlw4C30LOo5Dj+96fPGsf0zwzEDdQDXgq7Y=","salt":"j4RftdaS6RP22souHYWxFQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
78c2a932-be62-43b3-b3c4-48534c9c8846	\N	password	6cd9b564-fdc7-401f-bda1-b371a5062dbc	1785366846018	\N	{"value":"nIyLk6xfL61XhwMcI8fhTWTi4TCFU0RD5CAaMN/elss=","salt":"Z2FMOyFllIOcjXU7a+JSOQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
56941759-a041-4c4f-85ae-69fdf7accddb	\N	password	41123d37-da94-424e-b20b-3dce730679b1	1785706515958	\N	{"value":"8Qrpy13OAtZ8g77J1qyX+xpfPML5SabwA+338c3AfVQ=","salt":"9pInvPVrqQg1oNfwL7lWig==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
f8815c41-cfd8-487e-93d0-9b55f784faab	\N	password	73993873-ef95-4074-843b-ffd111c6754b	1785706820669	\N	{"value":"zWXH9wLthbN2AoHbT7377wIuSR6hKewTRAscQZGn0ew=","salt":"lDKdcrX0aoAYnWsMxaVljQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
76d67070-9cce-4fe5-9ff3-3855b6b1a164	\N	password	cba32bca-7c95-41a9-93c7-b7693f7b11ec	1785707176340	\N	{"value":"rfd1DTBRyZtmy6zptHPvAbxw65YnnH5yQLV2HNpkCJY=","salt":"3PDdmx5vt0qdwxq44CHh4w==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
70154d5f-41a8-49ca-bddf-4851cfa6cde1	\N	password	d0863ab4-7151-4f89-be1e-11bdbb5f88e0	1787036363536	\N	{"value":"VYhGDlsWNXpMmr3HMXfCQihL95n/8xXeO5vKJq3vLmQ=","salt":"E0ic5ybkBt07RB8t5Vk9IA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
8c8d787f-a8cb-421b-b8f9-13b6bca75c7d	\N	password	333079f6-fb21-494a-a789-7466d6afb6f3	1787387681097	\N	{"value":"YSr6ISu4jvJAWe6zvARkZPZa80cBfiKcqqEvUL9M4Zw=","salt":"zxR4L09OOqMYxEZE7f+G0Q==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
7de49ff5-568a-46bb-afc9-d921b120dc49	\N	password	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f	1787395447599	\N	{"value":"OQxvnusMKg6NeTpK/IkSWLHTwPjK3b/rYgdEfdX3bII=","salt":"yUJIGM0iM46bNf5qno0S5g==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
536af7f0-815a-462e-81dc-890474249efc	\N	password	1f5e14d8-4595-4946-971c-3815a1590104	1787394096380	\N	{"value":"5cdLm6sVwFPC8qCmoQKx9bx8UwQCuzBA8VxXP8ZXLAc=","salt":"oc/oRchaZLqLNuuJbz72Qw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
45bd646c-12f0-4726-b738-be1b70fef1d5	\N	password	fdb0ec96-f0b9-424d-a34e-7b4144979efa	1787397950763	\N	{"value":"WoE7bIWEiH+0N6ot/vexqkrch2Gs0umkDEVBp7A0Mzw=","salt":"EP7PsJGUvDbMM0fX8WJhEw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
ebb4a0af-0103-4808-9e79-6688fa282ccb	\N	password	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6	1788560076050	\N	{"value":"tT1mmXDl0u9eeRCb8DnmM5izpBPigK8ybVNDqo160+4=","salt":"FhbmLfDuDrIZgAfJMZOI+w==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
39d3ad7d-b1e9-4e39-a248-c217361a7251	\N	password	13f38f7f-47cb-44c6-b50a-06121ea1e302	1787400855147	\N	{"value":"WthLVn4RB0zlVc2KfW3USdowLDLDnbzUreSXdyzs1/U=","salt":"QXrCyUhUPbOJeSL8KEAXaQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
0f55ced2-69b0-4465-9805-57938ecace42	\N	password	a68eb3cd-a728-46a2-bb12-098613cdbb14	1787893395182	\N	{"value":"3i61b+9uQhdv1Yf7vfB5PuAqabWcGDoRS0D1NzJv9KA=","salt":"xdqcoj+AxKmYi3iZzhAyzA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
08d2bdc7-273c-4763-8afd-e93e79ce3232	\N	password	2c722c17-455e-4786-b0cf-6e88f7a66f81	1788011108707	\N	{"value":"EQ4kjzd7ewbZmxGhNEyP4qzuZrbAhLZ0Yvr4A32K1X8=","salt":"cBobPXHcbEOkn+uoMFk7MA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
06fff58a-bcb0-4a48-8699-d2743b845909	\N	password	9c4925e9-d004-4976-a024-8738729c17a6	1788275104388	\N	{"value":"XkAUWYkz6rsIcFLhjsuS5JhoFmS07lg8o9tYmUlKRAo=","salt":"0Jj/UYgtck/WZ5EChBTcgQ==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
fc89ef83-bd93-4886-af8f-c0bd6847136d	\N	password	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f	1788299552686	\N	{"value":"VubUcSc01arbLzSqov5nA1l3no5Gz7VeQvEkrX8Cjdo=","salt":"GsQWD/tF+jHDSz4H5T+eQA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
9ab6d6de-6577-4295-9021-b8806fb46b08	\N	password	ba7ae713-d025-4469-8d99-a29b0a7a4ae1	1788905008467	\N	{"value":"nHfVNNmiaK3oOuyWERm0cI+VjSOuymeVNcPgOcqfcaM=","salt":"PEeRfD4NCy+EhpAI/2x44A==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
36fc233b-5d73-491b-8784-e70eb963d284	\N	password	5c45254c-a739-45f2-8b79-3cd95a3747dc	1788905535373	\N	{"value":"fVTu5ZBHT+AaW3GqaQr2kkZieM7BYUgwM62kZYTU6Ik=","salt":"u7QvRfKASFGxocCjfMdR7Q==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
b82f871b-28b1-4f0d-b7f3-39391995bce8	\N	password	c50545e4-81a9-40b2-b90a-c3b8a651b70c	1788907317305	\N	{"value":"m56wwDgFDFIVjpte51QHmhanieNurjpKNi/6/4lkc5M=","salt":"E/liSN93I2lAigg320cWQw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
3985d9f5-1f78-4ade-8c55-dee394d925d4	\N	password	e7861bcc-140a-4894-a5c6-0661900bc42b	1788964976059	\N	{"value":"3EQHazBPhwO8ZwWlA6Ajov7C6HNb9nt7KT4uajACkwU=","salt":"uUh+VZ/6vkyn5Q8m3ZU24w==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
c0b7bd97-ef6a-4bff-9165-7701d33535fc	\N	password	9a683629-6c9e-403c-a7b9-ef683f2878f1	1788965320024	\N	{"value":"quOKXeE9zOUEm6FAfp8H9A5IUDqvA+3U+gS3T6C1awU=","salt":"kTO0u/74Kr/bX1hjZLap0g==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
17eef585-795e-421c-aac0-44c8b2cd6ece	\N	password	10921d32-7b89-46fa-ad2c-b8c9bfe2276b	1788995222822	\N	{"value":"5AYY03hzMvz9/B5DPzkCGhMKAsmwKfVVns/Q9fPC7q8=","salt":"fXQHD1MZRHJMBfOV7+oOTw==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
1148f05e-5cb6-4c0c-95d9-303f2a445a92	\N	password	6474a727-5e85-406d-9289-1fea2513c5da	1788995854829	\N	{"value":"OkOFc5FzBLhUOzCcoA1Zs/s23uyTH15I3ABK7yL1tsw=","salt":"iD7PWeCmE+3m+9zigjInfA==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
6662d01e-cc57-4490-acdb-31abb90bfd59	\N	password	1177d06d-97dc-4483-a8a7-ed4819b4a450	1789078961542	\N	{"value":"Ch3PrVXyV5Or+zINAAWeEZiPWKXl1wEgfUQyHMyWae8=","salt":"hwWHxVv5Rq9uKZfjL9ITkg==","additionalParameters":{}}	{"hashIterations":5,"algorithm":"argon2","additionalParameters":{"hashLength":["32"],"memory":["7168"],"type":["id"],"version":["1.3"],"parallelism":["1"]}}	10
\.


--
-- Data for Name: databasechangelog; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) FROM stdin;
1.0.0.Final-KEYCLOAK-5461	sthorger@redhat.com	META-INF/jpa-changelog-1.0.0.Final.xml	2026-06-29 11:10:31.48555	1	EXECUTED	9:6f1016664e21e16d26517a4418f5e3df	createTable tableName=APPLICATION_DEFAULT_ROLES; createTable tableName=CLIENT; createTable tableName=CLIENT_SESSION; createTable tableName=CLIENT_SESSION_ROLE; createTable tableName=COMPOSITE_ROLE; createTable tableName=CREDENTIAL; createTable tab...		\N	4.29.1	\N	\N	2731431181
1.0.0.Final-KEYCLOAK-5461	sthorger@redhat.com	META-INF/db2-jpa-changelog-1.0.0.Final.xml	2026-06-29 11:10:31.498955	2	MARK_RAN	9:828775b1596a07d1200ba1d49e5e3941	createTable tableName=APPLICATION_DEFAULT_ROLES; createTable tableName=CLIENT; createTable tableName=CLIENT_SESSION; createTable tableName=CLIENT_SESSION_ROLE; createTable tableName=COMPOSITE_ROLE; createTable tableName=CREDENTIAL; createTable tab...		\N	4.29.1	\N	\N	2731431181
1.1.0.Beta1	sthorger@redhat.com	META-INF/jpa-changelog-1.1.0.Beta1.xml	2026-06-29 11:10:31.524497	3	EXECUTED	9:5f090e44a7d595883c1fb61f4b41fd38	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=CLIENT_ATTRIBUTES; createTable tableName=CLIENT_SESSION_NOTE; createTable tableName=APP_NODE_REGISTRATIONS; addColumn table...		\N	4.29.1	\N	\N	2731431181
1.1.0.Final	sthorger@redhat.com	META-INF/jpa-changelog-1.1.0.Final.xml	2026-06-29 11:10:31.527044	4	EXECUTED	9:c07e577387a3d2c04d1adc9aaad8730e	renameColumn newColumnName=EVENT_TIME, oldColumnName=TIME, tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	2731431181
1.2.0.Beta1	psilva@redhat.com	META-INF/jpa-changelog-1.2.0.Beta1.xml	2026-06-29 11:10:31.594799	5	EXECUTED	9:b68ce996c655922dbcd2fe6b6ae72686	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=PROTOCOL_MAPPER; createTable tableName=PROTOCOL_MAPPER_CONFIG; createTable tableName=...		\N	4.29.1	\N	\N	2731431181
1.2.0.Beta1	psilva@redhat.com	META-INF/db2-jpa-changelog-1.2.0.Beta1.xml	2026-06-29 11:10:31.601608	6	MARK_RAN	9:543b5c9989f024fe35c6f6c5a97de88e	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION; createTable tableName=PROTOCOL_MAPPER; createTable tableName=PROTOCOL_MAPPER_CONFIG; createTable tableName=...		\N	4.29.1	\N	\N	2731431181
1.2.0.RC1	bburke@redhat.com	META-INF/jpa-changelog-1.2.0.CR1.xml	2026-06-29 11:10:31.652859	7	EXECUTED	9:765afebbe21cf5bbca048e632df38336	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=MIGRATION_MODEL; createTable tableName=IDENTITY_P...		\N	4.29.1	\N	\N	2731431181
1.2.0.RC1	bburke@redhat.com	META-INF/db2-jpa-changelog-1.2.0.CR1.xml	2026-06-29 11:10:31.656515	8	MARK_RAN	9:db4a145ba11a6fdaefb397f6dbf829a1	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=MIGRATION_MODEL; createTable tableName=IDENTITY_P...		\N	4.29.1	\N	\N	2731431181
1.2.0.Final	keycloak	META-INF/jpa-changelog-1.2.0.Final.xml	2026-06-29 11:10:31.660583	9	EXECUTED	9:9d05c7be10cdb873f8bcb41bc3a8ab23	update tableName=CLIENT; update tableName=CLIENT; update tableName=CLIENT		\N	4.29.1	\N	\N	2731431181
1.3.0	bburke@redhat.com	META-INF/jpa-changelog-1.3.0.xml	2026-06-29 11:10:31.729641	10	EXECUTED	9:18593702353128d53111f9b1ff0b82b8	delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete tableName=USER_SESSION; createTable tableName=ADMI...		\N	4.29.1	\N	\N	2731431181
1.4.0	bburke@redhat.com	META-INF/jpa-changelog-1.4.0.xml	2026-06-29 11:10:31.756099	11	EXECUTED	9:6122efe5f090e41a85c0f1c9e52cbb62	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	2731431181
1.4.0	bburke@redhat.com	META-INF/db2-jpa-changelog-1.4.0.xml	2026-06-29 11:10:31.758863	12	MARK_RAN	9:e1ff28bf7568451453f844c5d54bb0b5	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	2731431181
1.5.0	bburke@redhat.com	META-INF/jpa-changelog-1.5.0.xml	2026-06-29 11:10:31.769345	13	EXECUTED	9:7af32cd8957fbc069f796b61217483fd	delete tableName=CLIENT_SESSION_AUTH_STATUS; delete tableName=CLIENT_SESSION_ROLE; delete tableName=CLIENT_SESSION_PROT_MAPPER; delete tableName=CLIENT_SESSION_NOTE; delete tableName=CLIENT_SESSION; delete tableName=USER_SESSION_NOTE; delete table...		\N	4.29.1	\N	\N	2731431181
1.6.1_from15	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2026-06-29 11:10:31.778888	14	EXECUTED	9:6005e15e84714cd83226bf7879f54190	addColumn tableName=REALM; addColumn tableName=KEYCLOAK_ROLE; addColumn tableName=CLIENT; createTable tableName=OFFLINE_USER_SESSION; createTable tableName=OFFLINE_CLIENT_SESSION; addPrimaryKey constraintName=CONSTRAINT_OFFL_US_SES_PK2, tableName=...		\N	4.29.1	\N	\N	2731431181
1.6.1_from16-pre	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2026-06-29 11:10:31.779863	15	MARK_RAN	9:bf656f5a2b055d07f314431cae76f06c	delete tableName=OFFLINE_CLIENT_SESSION; delete tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
1.6.1_from16	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2026-06-29 11:10:31.781486	16	MARK_RAN	9:f8dadc9284440469dcf71e25ca6ab99b	dropPrimaryKey constraintName=CONSTRAINT_OFFLINE_US_SES_PK, tableName=OFFLINE_USER_SESSION; dropPrimaryKey constraintName=CONSTRAINT_OFFLINE_CL_SES_PK, tableName=OFFLINE_CLIENT_SESSION; addColumn tableName=OFFLINE_USER_SESSION; update tableName=OF...		\N	4.29.1	\N	\N	2731431181
1.6.1	mposolda@redhat.com	META-INF/jpa-changelog-1.6.1.xml	2026-06-29 11:10:31.783147	17	EXECUTED	9:d41d8cd98f00b204e9800998ecf8427e	empty		\N	4.29.1	\N	\N	2731431181
1.7.0	bburke@redhat.com	META-INF/jpa-changelog-1.7.0.xml	2026-06-29 11:10:31.805806	18	EXECUTED	9:3368ff0be4c2855ee2dd9ca813b38d8e	createTable tableName=KEYCLOAK_GROUP; createTable tableName=GROUP_ROLE_MAPPING; createTable tableName=GROUP_ATTRIBUTE; createTable tableName=USER_GROUP_MEMBERSHIP; createTable tableName=REALM_DEFAULT_GROUPS; addColumn tableName=IDENTITY_PROVIDER; ...		\N	4.29.1	\N	\N	2731431181
1.8.0	mposolda@redhat.com	META-INF/jpa-changelog-1.8.0.xml	2026-06-29 11:10:31.844025	19	EXECUTED	9:8ac2fb5dd030b24c0570a763ed75ed20	addColumn tableName=IDENTITY_PROVIDER; createTable tableName=CLIENT_TEMPLATE; createTable tableName=CLIENT_TEMPLATE_ATTRIBUTES; createTable tableName=TEMPLATE_SCOPE_MAPPING; dropNotNullConstraint columnName=CLIENT_ID, tableName=PROTOCOL_MAPPER; ad...		\N	4.29.1	\N	\N	2731431181
1.8.0-2	keycloak	META-INF/jpa-changelog-1.8.0.xml	2026-06-29 11:10:31.847262	20	EXECUTED	9:f91ddca9b19743db60e3057679810e6c	dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; update tableName=CREDENTIAL		\N	4.29.1	\N	\N	2731431181
1.8.0	mposolda@redhat.com	META-INF/db2-jpa-changelog-1.8.0.xml	2026-06-29 11:10:31.849091	21	MARK_RAN	9:831e82914316dc8a57dc09d755f23c51	addColumn tableName=IDENTITY_PROVIDER; createTable tableName=CLIENT_TEMPLATE; createTable tableName=CLIENT_TEMPLATE_ATTRIBUTES; createTable tableName=TEMPLATE_SCOPE_MAPPING; dropNotNullConstraint columnName=CLIENT_ID, tableName=PROTOCOL_MAPPER; ad...		\N	4.29.1	\N	\N	2731431181
1.8.0-2	keycloak	META-INF/db2-jpa-changelog-1.8.0.xml	2026-06-29 11:10:31.851005	22	MARK_RAN	9:f91ddca9b19743db60e3057679810e6c	dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; update tableName=CREDENTIAL		\N	4.29.1	\N	\N	2731431181
1.9.0	mposolda@redhat.com	META-INF/jpa-changelog-1.9.0.xml	2026-06-29 11:10:31.918295	23	EXECUTED	9:bc3d0f9e823a69dc21e23e94c7a94bb1	update tableName=REALM; update tableName=REALM; update tableName=REALM; update tableName=REALM; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=REALM; update tableName=REALM; customChange; dr...		\N	4.29.1	\N	\N	2731431181
1.9.1	keycloak	META-INF/jpa-changelog-1.9.1.xml	2026-06-29 11:10:31.922213	24	EXECUTED	9:c9999da42f543575ab790e76439a2679	modifyDataType columnName=PRIVATE_KEY, tableName=REALM; modifyDataType columnName=PUBLIC_KEY, tableName=REALM; modifyDataType columnName=CERTIFICATE, tableName=REALM		\N	4.29.1	\N	\N	2731431181
1.9.1	keycloak	META-INF/db2-jpa-changelog-1.9.1.xml	2026-06-29 11:10:31.923384	25	MARK_RAN	9:0d6c65c6f58732d81569e77b10ba301d	modifyDataType columnName=PRIVATE_KEY, tableName=REALM; modifyDataType columnName=CERTIFICATE, tableName=REALM		\N	4.29.1	\N	\N	2731431181
1.9.2	keycloak	META-INF/jpa-changelog-1.9.2.xml	2026-06-29 11:10:32.210002	26	EXECUTED	9:fc576660fc016ae53d2d4778d84d86d0	createIndex indexName=IDX_USER_EMAIL, tableName=USER_ENTITY; createIndex indexName=IDX_USER_ROLE_MAPPING, tableName=USER_ROLE_MAPPING; createIndex indexName=IDX_USER_GROUP_MAPPING, tableName=USER_GROUP_MEMBERSHIP; createIndex indexName=IDX_USER_CO...		\N	4.29.1	\N	\N	2731431181
authz-2.0.0	psilva@redhat.com	META-INF/jpa-changelog-authz-2.0.0.xml	2026-06-29 11:10:32.240308	27	EXECUTED	9:43ed6b0da89ff77206289e87eaa9c024	createTable tableName=RESOURCE_SERVER; addPrimaryKey constraintName=CONSTRAINT_FARS, tableName=RESOURCE_SERVER; addUniqueConstraint constraintName=UK_AU8TT6T700S9V50BU18WS5HA6, tableName=RESOURCE_SERVER; createTable tableName=RESOURCE_SERVER_RESOU...		\N	4.29.1	\N	\N	2731431181
authz-2.5.1	psilva@redhat.com	META-INF/jpa-changelog-authz-2.5.1.xml	2026-06-29 11:10:32.242046	28	EXECUTED	9:44bae577f551b3738740281eceb4ea70	update tableName=RESOURCE_SERVER_POLICY		\N	4.29.1	\N	\N	2731431181
2.1.0-KEYCLOAK-5461	bburke@redhat.com	META-INF/jpa-changelog-2.1.0.xml	2026-06-29 11:10:32.2666	29	EXECUTED	9:bd88e1f833df0420b01e114533aee5e8	createTable tableName=BROKER_LINK; createTable tableName=FED_USER_ATTRIBUTE; createTable tableName=FED_USER_CONSENT; createTable tableName=FED_USER_CONSENT_ROLE; createTable tableName=FED_USER_CONSENT_PROT_MAPPER; createTable tableName=FED_USER_CR...		\N	4.29.1	\N	\N	2731431181
2.2.0	bburke@redhat.com	META-INF/jpa-changelog-2.2.0.xml	2026-06-29 11:10:32.272341	30	EXECUTED	9:a7022af5267f019d020edfe316ef4371	addColumn tableName=ADMIN_EVENT_ENTITY; createTable tableName=CREDENTIAL_ATTRIBUTE; createTable tableName=FED_CREDENTIAL_ATTRIBUTE; modifyDataType columnName=VALUE, tableName=CREDENTIAL; addForeignKeyConstraint baseTableName=FED_CREDENTIAL_ATTRIBU...		\N	4.29.1	\N	\N	2731431181
2.3.0	bburke@redhat.com	META-INF/jpa-changelog-2.3.0.xml	2026-06-29 11:10:32.27981	31	EXECUTED	9:fc155c394040654d6a79227e56f5e25a	createTable tableName=FEDERATED_USER; addPrimaryKey constraintName=CONSTR_FEDERATED_USER, tableName=FEDERATED_USER; dropDefaultValue columnName=TOTP, tableName=USER_ENTITY; dropColumn columnName=TOTP, tableName=USER_ENTITY; addColumn tableName=IDE...		\N	4.29.1	\N	\N	2731431181
2.4.0	bburke@redhat.com	META-INF/jpa-changelog-2.4.0.xml	2026-06-29 11:10:32.282278	32	EXECUTED	9:eac4ffb2a14795e5dc7b426063e54d88	customChange		\N	4.29.1	\N	\N	2731431181
2.5.0	bburke@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2026-06-29 11:10:32.28755	33	EXECUTED	9:54937c05672568c4c64fc9524c1e9462	customChange; modifyDataType columnName=USER_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
2.5.0-unicode-oracle	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2026-06-29 11:10:32.289492	34	MARK_RAN	9:3a32bace77c84d7678d035a7f5a8084e	modifyDataType columnName=DESCRIPTION, tableName=AUTHENTICATION_FLOW; modifyDataType columnName=DESCRIPTION, tableName=CLIENT_TEMPLATE; modifyDataType columnName=DESCRIPTION, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=DESCRIPTION,...		\N	4.29.1	\N	\N	2731431181
2.5.0-unicode-other-dbs	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2026-06-29 11:10:32.308264	35	EXECUTED	9:33d72168746f81f98ae3a1e8e0ca3554	modifyDataType columnName=DESCRIPTION, tableName=AUTHENTICATION_FLOW; modifyDataType columnName=DESCRIPTION, tableName=CLIENT_TEMPLATE; modifyDataType columnName=DESCRIPTION, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=DESCRIPTION,...		\N	4.29.1	\N	\N	2731431181
2.5.0-duplicate-email-support	slawomir@dabek.name	META-INF/jpa-changelog-2.5.0.xml	2026-06-29 11:10:32.310953	36	EXECUTED	9:61b6d3d7a4c0e0024b0c839da283da0c	addColumn tableName=REALM		\N	4.29.1	\N	\N	2731431181
2.5.0-unique-group-names	hmlnarik@redhat.com	META-INF/jpa-changelog-2.5.0.xml	2026-06-29 11:10:32.313052	37	EXECUTED	9:8dcac7bdf7378e7d823cdfddebf72fda	addUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	2731431181
2.5.1	bburke@redhat.com	META-INF/jpa-changelog-2.5.1.xml	2026-06-29 11:10:32.314465	38	EXECUTED	9:a2b870802540cb3faa72098db5388af3	addColumn tableName=FED_USER_CONSENT		\N	4.29.1	\N	\N	2731431181
3.0.0	bburke@redhat.com	META-INF/jpa-changelog-3.0.0.xml	2026-06-29 11:10:32.31595	39	EXECUTED	9:132a67499ba24bcc54fb5cbdcfe7e4c0	addColumn tableName=IDENTITY_PROVIDER		\N	4.29.1	\N	\N	2731431181
3.2.0-fix	keycloak	META-INF/jpa-changelog-3.2.0.xml	2026-06-29 11:10:32.316474	40	MARK_RAN	9:938f894c032f5430f2b0fafb1a243462	addNotNullConstraint columnName=REALM_ID, tableName=CLIENT_INITIAL_ACCESS		\N	4.29.1	\N	\N	2731431181
3.2.0-fix-with-keycloak-5416	keycloak	META-INF/jpa-changelog-3.2.0.xml	2026-06-29 11:10:32.317238	41	MARK_RAN	9:845c332ff1874dc5d35974b0babf3006	dropIndex indexName=IDX_CLIENT_INIT_ACC_REALM, tableName=CLIENT_INITIAL_ACCESS; addNotNullConstraint columnName=REALM_ID, tableName=CLIENT_INITIAL_ACCESS; createIndex indexName=IDX_CLIENT_INIT_ACC_REALM, tableName=CLIENT_INITIAL_ACCESS		\N	4.29.1	\N	\N	2731431181
3.2.0-fix-offline-sessions	hmlnarik	META-INF/jpa-changelog-3.2.0.xml	2026-06-29 11:10:32.319279	42	EXECUTED	9:fc86359c079781adc577c5a217e4d04c	customChange		\N	4.29.1	\N	\N	2731431181
3.2.0-fixed	keycloak	META-INF/jpa-changelog-3.2.0.xml	2026-06-29 11:10:33.259202	43	EXECUTED	9:59a64800e3c0d09b825f8a3b444fa8f4	addColumn tableName=REALM; dropPrimaryKey constraintName=CONSTRAINT_OFFL_CL_SES_PK2, tableName=OFFLINE_CLIENT_SESSION; dropColumn columnName=CLIENT_SESSION_ID, tableName=OFFLINE_CLIENT_SESSION; addPrimaryKey constraintName=CONSTRAINT_OFFL_CL_SES_P...		\N	4.29.1	\N	\N	2731431181
3.3.0	keycloak	META-INF/jpa-changelog-3.3.0.xml	2026-06-29 11:10:33.261555	44	EXECUTED	9:d48d6da5c6ccf667807f633fe489ce88	addColumn tableName=USER_ENTITY		\N	4.29.1	\N	\N	2731431181
authz-3.4.0.CR1-resource-server-pk-change-part1	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2026-06-29 11:10:33.263515	45	EXECUTED	9:dde36f7973e80d71fceee683bc5d2951	addColumn tableName=RESOURCE_SERVER_POLICY; addColumn tableName=RESOURCE_SERVER_RESOURCE; addColumn tableName=RESOURCE_SERVER_SCOPE		\N	4.29.1	\N	\N	2731431181
authz-3.4.0.CR1-resource-server-pk-change-part2-KEYCLOAK-6095	hmlnarik@redhat.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2026-06-29 11:10:33.265379	46	EXECUTED	9:b855e9b0a406b34fa323235a0cf4f640	customChange		\N	4.29.1	\N	\N	2731431181
authz-3.4.0.CR1-resource-server-pk-change-part3-fixed	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2026-06-29 11:10:33.265998	47	MARK_RAN	9:51abbacd7b416c50c4421a8cabf7927e	dropIndex indexName=IDX_RES_SERV_POL_RES_SERV, tableName=RESOURCE_SERVER_POLICY; dropIndex indexName=IDX_RES_SRV_RES_RES_SRV, tableName=RESOURCE_SERVER_RESOURCE; dropIndex indexName=IDX_RES_SRV_SCOPE_RES_SRV, tableName=RESOURCE_SERVER_SCOPE		\N	4.29.1	\N	\N	2731431181
authz-3.4.0.CR1-resource-server-pk-change-part3-fixed-nodropindex	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2026-06-29 11:10:33.333172	48	EXECUTED	9:bdc99e567b3398bac83263d375aad143	addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, tableName=RESOURCE_SERVER_POLICY; addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, tableName=RESOURCE_SERVER_RESOURCE; addNotNullConstraint columnName=RESOURCE_SERVER_CLIENT_ID, ...		\N	4.29.1	\N	\N	2731431181
authn-3.4.0.CR1-refresh-token-max-reuse	glavoie@gmail.com	META-INF/jpa-changelog-authz-3.4.0.CR1.xml	2026-06-29 11:10:33.335398	49	EXECUTED	9:d198654156881c46bfba39abd7769e69	addColumn tableName=REALM		\N	4.29.1	\N	\N	2731431181
3.4.0	keycloak	META-INF/jpa-changelog-3.4.0.xml	2026-06-29 11:10:33.354185	50	EXECUTED	9:cfdd8736332ccdd72c5256ccb42335db	addPrimaryKey constraintName=CONSTRAINT_REALM_DEFAULT_ROLES, tableName=REALM_DEFAULT_ROLES; addPrimaryKey constraintName=CONSTRAINT_COMPOSITE_ROLE, tableName=COMPOSITE_ROLE; addPrimaryKey constraintName=CONSTR_REALM_DEFAULT_GROUPS, tableName=REALM...		\N	4.29.1	\N	\N	2731431181
3.4.0-KEYCLOAK-5230	hmlnarik@redhat.com	META-INF/jpa-changelog-3.4.0.xml	2026-06-29 11:10:33.564023	51	EXECUTED	9:7c84de3d9bd84d7f077607c1a4dcb714	createIndex indexName=IDX_FU_ATTRIBUTE, tableName=FED_USER_ATTRIBUTE; createIndex indexName=IDX_FU_CONSENT, tableName=FED_USER_CONSENT; createIndex indexName=IDX_FU_CONSENT_RU, tableName=FED_USER_CONSENT; createIndex indexName=IDX_FU_CREDENTIAL, t...		\N	4.29.1	\N	\N	2731431181
3.4.1	psilva@redhat.com	META-INF/jpa-changelog-3.4.1.xml	2026-06-29 11:10:33.565672	52	EXECUTED	9:5a6bb36cbefb6a9d6928452c0852af2d	modifyDataType columnName=VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
3.4.2	keycloak	META-INF/jpa-changelog-3.4.2.xml	2026-06-29 11:10:33.566864	53	EXECUTED	9:8f23e334dbc59f82e0a328373ca6ced0	update tableName=REALM		\N	4.29.1	\N	\N	2731431181
3.4.2-KEYCLOAK-5172	mkanis@redhat.com	META-INF/jpa-changelog-3.4.2.xml	2026-06-29 11:10:33.567897	54	EXECUTED	9:9156214268f09d970cdf0e1564d866af	update tableName=CLIENT		\N	4.29.1	\N	\N	2731431181
4.0.0-KEYCLOAK-6335	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2026-06-29 11:10:33.57046	55	EXECUTED	9:db806613b1ed154826c02610b7dbdf74	createTable tableName=CLIENT_AUTH_FLOW_BINDINGS; addPrimaryKey constraintName=C_CLI_FLOW_BIND, tableName=CLIENT_AUTH_FLOW_BINDINGS		\N	4.29.1	\N	\N	2731431181
4.0.0-CLEANUP-UNUSED-TABLE	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2026-06-29 11:10:33.572195	56	EXECUTED	9:229a041fb72d5beac76bb94a5fa709de	dropTable tableName=CLIENT_IDENTITY_PROV_MAPPING		\N	4.29.1	\N	\N	2731431181
4.0.0-KEYCLOAK-6228	bburke@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2026-06-29 11:10:33.598565	57	EXECUTED	9:079899dade9c1e683f26b2aa9ca6ff04	dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; dropNotNullConstraint columnName=CLIENT_ID, tableName=USER_CONSENT; addColumn tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHO...		\N	4.29.1	\N	\N	2731431181
4.0.0-KEYCLOAK-5579-fixed	mposolda@redhat.com	META-INF/jpa-changelog-4.0.0.xml	2026-06-29 11:10:33.790788	58	EXECUTED	9:139b79bcbbfe903bb1c2d2a4dbf001d9	dropForeignKeyConstraint baseTableName=CLIENT_TEMPLATE_ATTRIBUTES, constraintName=FK_CL_TEMPL_ATTR_TEMPL; renameTable newTableName=CLIENT_SCOPE_ATTRIBUTES, oldTableName=CLIENT_TEMPLATE_ATTRIBUTES; renameColumn newColumnName=SCOPE_ID, oldColumnName...		\N	4.29.1	\N	\N	2731431181
authz-4.0.0.CR1	psilva@redhat.com	META-INF/jpa-changelog-authz-4.0.0.CR1.xml	2026-06-29 11:10:33.800863	59	EXECUTED	9:b55738ad889860c625ba2bf483495a04	createTable tableName=RESOURCE_SERVER_PERM_TICKET; addPrimaryKey constraintName=CONSTRAINT_FAPMT, tableName=RESOURCE_SERVER_PERM_TICKET; addForeignKeyConstraint baseTableName=RESOURCE_SERVER_PERM_TICKET, constraintName=FK_FRSRHO213XCX4WNKOG82SSPMT...		\N	4.29.1	\N	\N	2731431181
authz-4.0.0.Beta3	psilva@redhat.com	META-INF/jpa-changelog-authz-4.0.0.Beta3.xml	2026-06-29 11:10:33.803014	60	EXECUTED	9:e0057eac39aa8fc8e09ac6cfa4ae15fe	addColumn tableName=RESOURCE_SERVER_POLICY; addColumn tableName=RESOURCE_SERVER_PERM_TICKET; addForeignKeyConstraint baseTableName=RESOURCE_SERVER_PERM_TICKET, constraintName=FK_FRSRPO2128CX4WNKOG82SSRFY, referencedTableName=RESOURCE_SERVER_POLICY		\N	4.29.1	\N	\N	2731431181
authz-4.2.0.Final	mhajas@redhat.com	META-INF/jpa-changelog-authz-4.2.0.Final.xml	2026-06-29 11:10:33.80622	61	EXECUTED	9:42a33806f3a0443fe0e7feeec821326c	createTable tableName=RESOURCE_URIS; addForeignKeyConstraint baseTableName=RESOURCE_URIS, constraintName=FK_RESOURCE_SERVER_URIS, referencedTableName=RESOURCE_SERVER_RESOURCE; customChange; dropColumn columnName=URI, tableName=RESOURCE_SERVER_RESO...		\N	4.29.1	\N	\N	2731431181
authz-4.2.0.Final-KEYCLOAK-9944	hmlnarik@redhat.com	META-INF/jpa-changelog-authz-4.2.0.Final.xml	2026-06-29 11:10:33.808178	62	EXECUTED	9:9968206fca46eecc1f51db9c024bfe56	addPrimaryKey constraintName=CONSTRAINT_RESOUR_URIS_PK, tableName=RESOURCE_URIS		\N	4.29.1	\N	\N	2731431181
4.2.0-KEYCLOAK-6313	wadahiro@gmail.com	META-INF/jpa-changelog-4.2.0.xml	2026-06-29 11:10:33.809308	63	EXECUTED	9:92143a6daea0a3f3b8f598c97ce55c3d	addColumn tableName=REQUIRED_ACTION_PROVIDER		\N	4.29.1	\N	\N	2731431181
4.3.0-KEYCLOAK-7984	wadahiro@gmail.com	META-INF/jpa-changelog-4.3.0.xml	2026-06-29 11:10:33.810317	64	EXECUTED	9:82bab26a27195d889fb0429003b18f40	update tableName=REQUIRED_ACTION_PROVIDER		\N	4.29.1	\N	\N	2731431181
4.6.0-KEYCLOAK-7950	psilva@redhat.com	META-INF/jpa-changelog-4.6.0.xml	2026-06-29 11:10:33.811266	65	EXECUTED	9:e590c88ddc0b38b0ae4249bbfcb5abc3	update tableName=RESOURCE_SERVER_RESOURCE		\N	4.29.1	\N	\N	2731431181
4.6.0-KEYCLOAK-8377	keycloak	META-INF/jpa-changelog-4.6.0.xml	2026-06-29 11:10:33.831882	66	EXECUTED	9:5c1f475536118dbdc38d5d7977950cc0	createTable tableName=ROLE_ATTRIBUTE; addPrimaryKey constraintName=CONSTRAINT_ROLE_ATTRIBUTE_PK, tableName=ROLE_ATTRIBUTE; addForeignKeyConstraint baseTableName=ROLE_ATTRIBUTE, constraintName=FK_ROLE_ATTRIBUTE_ID, referencedTableName=KEYCLOAK_ROLE...		\N	4.29.1	\N	\N	2731431181
4.6.0-KEYCLOAK-8555	gideonray@gmail.com	META-INF/jpa-changelog-4.6.0.xml	2026-06-29 11:10:33.85036	67	EXECUTED	9:e7c9f5f9c4d67ccbbcc215440c718a17	createIndex indexName=IDX_COMPONENT_PROVIDER_TYPE, tableName=COMPONENT		\N	4.29.1	\N	\N	2731431181
4.7.0-KEYCLOAK-1267	sguilhen@redhat.com	META-INF/jpa-changelog-4.7.0.xml	2026-06-29 11:10:33.852409	68	EXECUTED	9:88e0bfdda924690d6f4e430c53447dd5	addColumn tableName=REALM		\N	4.29.1	\N	\N	2731431181
4.7.0-KEYCLOAK-7275	keycloak	META-INF/jpa-changelog-4.7.0.xml	2026-06-29 11:10:33.874624	69	EXECUTED	9:f53177f137e1c46b6a88c59ec1cb5218	renameColumn newColumnName=CREATED_ON, oldColumnName=LAST_SESSION_REFRESH, tableName=OFFLINE_USER_SESSION; addNotNullConstraint columnName=CREATED_ON, tableName=OFFLINE_USER_SESSION; addColumn tableName=OFFLINE_USER_SESSION; customChange; createIn...		\N	4.29.1	\N	\N	2731431181
4.8.0-KEYCLOAK-8835	sguilhen@redhat.com	META-INF/jpa-changelog-4.8.0.xml	2026-06-29 11:10:33.87866	70	EXECUTED	9:a74d33da4dc42a37ec27121580d1459f	addNotNullConstraint columnName=SSO_MAX_LIFESPAN_REMEMBER_ME, tableName=REALM; addNotNullConstraint columnName=SSO_IDLE_TIMEOUT_REMEMBER_ME, tableName=REALM		\N	4.29.1	\N	\N	2731431181
authz-7.0.0-KEYCLOAK-10443	psilva@redhat.com	META-INF/jpa-changelog-authz-7.0.0.xml	2026-06-29 11:10:33.880491	71	EXECUTED	9:fd4ade7b90c3b67fae0bfcfcb42dfb5f	addColumn tableName=RESOURCE_SERVER		\N	4.29.1	\N	\N	2731431181
8.0.0-adding-credential-columns	keycloak	META-INF/jpa-changelog-8.0.0.xml	2026-06-29 11:10:33.883581	72	EXECUTED	9:aa072ad090bbba210d8f18781b8cebf4	addColumn tableName=CREDENTIAL; addColumn tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	2731431181
8.0.0-updating-credential-data-not-oracle-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2026-06-29 11:10:33.887098	73	EXECUTED	9:1ae6be29bab7c2aa376f6983b932be37	update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	2731431181
8.0.0-updating-credential-data-oracle-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2026-06-29 11:10:33.888076	74	MARK_RAN	9:14706f286953fc9a25286dbd8fb30d97	update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL; update tableName=FED_USER_CREDENTIAL		\N	4.29.1	\N	\N	2731431181
8.0.0-credential-cleanup-fixed	keycloak	META-INF/jpa-changelog-8.0.0.xml	2026-06-29 11:10:33.895586	75	EXECUTED	9:2b9cc12779be32c5b40e2e67711a218b	dropDefaultValue columnName=COUNTER, tableName=CREDENTIAL; dropDefaultValue columnName=DIGITS, tableName=CREDENTIAL; dropDefaultValue columnName=PERIOD, tableName=CREDENTIAL; dropDefaultValue columnName=ALGORITHM, tableName=CREDENTIAL; dropColumn ...		\N	4.29.1	\N	\N	2731431181
8.0.0-resource-tag-support	keycloak	META-INF/jpa-changelog-8.0.0.xml	2026-06-29 11:10:33.916672	76	EXECUTED	9:91fa186ce7a5af127a2d7a91ee083cc5	addColumn tableName=MIGRATION_MODEL; createIndex indexName=IDX_UPDATE_TIME, tableName=MIGRATION_MODEL		\N	4.29.1	\N	\N	2731431181
9.0.0-always-display-client	keycloak	META-INF/jpa-changelog-9.0.0.xml	2026-06-29 11:10:33.918415	77	EXECUTED	9:6335e5c94e83a2639ccd68dd24e2e5ad	addColumn tableName=CLIENT		\N	4.29.1	\N	\N	2731431181
9.0.0-drop-constraints-for-column-increase	keycloak	META-INF/jpa-changelog-9.0.0.xml	2026-06-29 11:10:33.91904	78	MARK_RAN	9:6bdb5658951e028bfe16fa0a8228b530	dropUniqueConstraint constraintName=UK_FRSR6T700S9V50BU18WS5PMT, tableName=RESOURCE_SERVER_PERM_TICKET; dropUniqueConstraint constraintName=UK_FRSR6T700S9V50BU18WS5HA6, tableName=RESOURCE_SERVER_RESOURCE; dropPrimaryKey constraintName=CONSTRAINT_O...		\N	4.29.1	\N	\N	2731431181
9.0.0-increase-column-size-federated-fk	keycloak	META-INF/jpa-changelog-9.0.0.xml	2026-06-29 11:10:33.927657	79	EXECUTED	9:d5bc15a64117ccad481ce8792d4c608f	modifyDataType columnName=CLIENT_ID, tableName=FED_USER_CONSENT; modifyDataType columnName=CLIENT_REALM_CONSTRAINT, tableName=KEYCLOAK_ROLE; modifyDataType columnName=OWNER, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=CLIENT_ID, ta...		\N	4.29.1	\N	\N	2731431181
9.0.0-recreate-constraints-after-column-increase	keycloak	META-INF/jpa-changelog-9.0.0.xml	2026-06-29 11:10:33.92861	80	MARK_RAN	9:077cba51999515f4d3e7ad5619ab592c	addNotNullConstraint columnName=CLIENT_ID, tableName=OFFLINE_CLIENT_SESSION; addNotNullConstraint columnName=OWNER, tableName=RESOURCE_SERVER_PERM_TICKET; addNotNullConstraint columnName=REQUESTER, tableName=RESOURCE_SERVER_PERM_TICKET; addNotNull...		\N	4.29.1	\N	\N	2731431181
9.0.1-add-index-to-client.client_id	keycloak	META-INF/jpa-changelog-9.0.1.xml	2026-06-29 11:10:33.949012	81	EXECUTED	9:be969f08a163bf47c6b9e9ead8ac2afb	createIndex indexName=IDX_CLIENT_ID, tableName=CLIENT		\N	4.29.1	\N	\N	2731431181
9.0.1-KEYCLOAK-12579-drop-constraints	keycloak	META-INF/jpa-changelog-9.0.1.xml	2026-06-29 11:10:33.94969	82	MARK_RAN	9:6d3bb4408ba5a72f39bd8a0b301ec6e3	dropUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	2731431181
9.0.1-KEYCLOAK-12579-add-not-null-constraint	keycloak	META-INF/jpa-changelog-9.0.1.xml	2026-06-29 11:10:33.95163	83	EXECUTED	9:966bda61e46bebf3cc39518fbed52fa7	addNotNullConstraint columnName=PARENT_GROUP, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	2731431181
9.0.1-KEYCLOAK-12579-recreate-constraints	keycloak	META-INF/jpa-changelog-9.0.1.xml	2026-06-29 11:10:33.952206	84	MARK_RAN	9:8dcac7bdf7378e7d823cdfddebf72fda	addUniqueConstraint constraintName=SIBLING_NAMES, tableName=KEYCLOAK_GROUP		\N	4.29.1	\N	\N	2731431181
9.0.1-add-index-to-events	keycloak	META-INF/jpa-changelog-9.0.1.xml	2026-06-29 11:10:33.974554	85	EXECUTED	9:7d93d602352a30c0c317e6a609b56599	createIndex indexName=IDX_EVENT_TIME, tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	2731431181
map-remove-ri	keycloak	META-INF/jpa-changelog-11.0.0.xml	2026-06-29 11:10:33.976641	86	EXECUTED	9:71c5969e6cdd8d7b6f47cebc86d37627	dropForeignKeyConstraint baseTableName=REALM, constraintName=FK_TRAF444KK6QRKMS7N56AIWQ5Y; dropForeignKeyConstraint baseTableName=KEYCLOAK_ROLE, constraintName=FK_KJHO5LE2C0RAL09FL8CM9WFW9		\N	4.29.1	\N	\N	2731431181
map-remove-ri	keycloak	META-INF/jpa-changelog-12.0.0.xml	2026-06-29 11:10:33.979517	87	EXECUTED	9:a9ba7d47f065f041b7da856a81762021	dropForeignKeyConstraint baseTableName=REALM_DEFAULT_GROUPS, constraintName=FK_DEF_GROUPS_GROUP; dropForeignKeyConstraint baseTableName=REALM_DEFAULT_ROLES, constraintName=FK_H4WPD7W4HSOOLNI3H0SW7BTJE; dropForeignKeyConstraint baseTableName=CLIENT...		\N	4.29.1	\N	\N	2731431181
12.1.0-add-realm-localization-table	keycloak	META-INF/jpa-changelog-12.0.0.xml	2026-06-29 11:10:33.983268	88	EXECUTED	9:fffabce2bc01e1a8f5110d5278500065	createTable tableName=REALM_LOCALIZATIONS; addPrimaryKey tableName=REALM_LOCALIZATIONS		\N	4.29.1	\N	\N	2731431181
default-roles	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:33.985978	89	EXECUTED	9:fa8a5b5445e3857f4b010bafb5009957	addColumn tableName=REALM; customChange		\N	4.29.1	\N	\N	2731431181
default-roles-cleanup	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:33.988586	90	EXECUTED	9:67ac3241df9a8582d591c5ed87125f39	dropTable tableName=REALM_DEFAULT_ROLES; dropTable tableName=CLIENT_DEFAULT_ROLES		\N	4.29.1	\N	\N	2731431181
13.0.0-KEYCLOAK-16844	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.011452	91	EXECUTED	9:ad1194d66c937e3ffc82386c050ba089	createIndex indexName=IDX_OFFLINE_USS_PRELOAD, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
map-remove-ri-13.0.0	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.014775	92	EXECUTED	9:d9be619d94af5a2f5d07b9f003543b91	dropForeignKeyConstraint baseTableName=DEFAULT_CLIENT_SCOPE, constraintName=FK_R_DEF_CLI_SCOPE_SCOPE; dropForeignKeyConstraint baseTableName=CLIENT_SCOPE_CLIENT, constraintName=FK_C_CLI_SCOPE_SCOPE; dropForeignKeyConstraint baseTableName=CLIENT_SC...		\N	4.29.1	\N	\N	2731431181
13.0.0-KEYCLOAK-17992-drop-constraints	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.015442	93	MARK_RAN	9:544d201116a0fcc5a5da0925fbbc3bde	dropPrimaryKey constraintName=C_CLI_SCOPE_BIND, tableName=CLIENT_SCOPE_CLIENT; dropIndex indexName=IDX_CLSCOPE_CL, tableName=CLIENT_SCOPE_CLIENT; dropIndex indexName=IDX_CL_CLSCOPE, tableName=CLIENT_SCOPE_CLIENT		\N	4.29.1	\N	\N	2731431181
13.0.0-increase-column-size-federated	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.018998	94	EXECUTED	9:43c0c1055b6761b4b3e89de76d612ccf	modifyDataType columnName=CLIENT_ID, tableName=CLIENT_SCOPE_CLIENT; modifyDataType columnName=SCOPE_ID, tableName=CLIENT_SCOPE_CLIENT		\N	4.29.1	\N	\N	2731431181
13.0.0-KEYCLOAK-17992-recreate-constraints	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.019886	95	MARK_RAN	9:8bd711fd0330f4fe980494ca43ab1139	addNotNullConstraint columnName=CLIENT_ID, tableName=CLIENT_SCOPE_CLIENT; addNotNullConstraint columnName=SCOPE_ID, tableName=CLIENT_SCOPE_CLIENT; addPrimaryKey constraintName=C_CLI_SCOPE_BIND, tableName=CLIENT_SCOPE_CLIENT; createIndex indexName=...		\N	4.29.1	\N	\N	2731431181
json-string-accomodation-fixed	keycloak	META-INF/jpa-changelog-13.0.0.xml	2026-06-29 11:10:34.022671	96	EXECUTED	9:e07d2bc0970c348bb06fb63b1f82ddbf	addColumn tableName=REALM_ATTRIBUTE; update tableName=REALM_ATTRIBUTE; dropColumn columnName=VALUE, tableName=REALM_ATTRIBUTE; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=REALM_ATTRIBUTE		\N	4.29.1	\N	\N	2731431181
14.0.0-KEYCLOAK-11019	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.086496	97	EXECUTED	9:24fb8611e97f29989bea412aa38d12b7	createIndex indexName=IDX_OFFLINE_CSS_PRELOAD, tableName=OFFLINE_CLIENT_SESSION; createIndex indexName=IDX_OFFLINE_USS_BY_USER, tableName=OFFLINE_USER_SESSION; createIndex indexName=IDX_OFFLINE_USS_BY_USERSESS, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
14.0.0-KEYCLOAK-18286	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.087247	98	MARK_RAN	9:259f89014ce2506ee84740cbf7163aa7	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
14.0.0-KEYCLOAK-18286-revert	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.093049	99	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
14.0.0-KEYCLOAK-18286-supported-dbs	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.118073	100	EXECUTED	9:60ca84a0f8c94ec8c3504a5a3bc88ee8	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
14.0.0-KEYCLOAK-18286-unsupported-dbs	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.119172	101	MARK_RAN	9:d3d977031d431db16e2c181ce49d73e9	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
KEYCLOAK-17267-add-index-to-user-attributes	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.145528	102	EXECUTED	9:0b305d8d1277f3a89a0a53a659ad274c	createIndex indexName=IDX_USER_ATTRIBUTE_NAME, tableName=USER_ATTRIBUTE		\N	4.29.1	\N	\N	2731431181
KEYCLOAK-18146-add-saml-art-binding-identifier	keycloak	META-INF/jpa-changelog-14.0.0.xml	2026-06-29 11:10:34.147829	103	EXECUTED	9:2c374ad2cdfe20e2905a84c8fac48460	customChange		\N	4.29.1	\N	\N	2731431181
15.0.0-KEYCLOAK-18467	keycloak	META-INF/jpa-changelog-15.0.0.xml	2026-06-29 11:10:34.150697	104	EXECUTED	9:47a760639ac597360a8219f5b768b4de	addColumn tableName=REALM_LOCALIZATIONS; update tableName=REALM_LOCALIZATIONS; dropColumn columnName=TEXTS, tableName=REALM_LOCALIZATIONS; renameColumn newColumnName=TEXTS, oldColumnName=TEXTS_NEW, tableName=REALM_LOCALIZATIONS; addNotNullConstrai...		\N	4.29.1	\N	\N	2731431181
17.0.0-9562	keycloak	META-INF/jpa-changelog-17.0.0.xml	2026-06-29 11:10:34.172144	105	EXECUTED	9:a6272f0576727dd8cad2522335f5d99e	createIndex indexName=IDX_USER_SERVICE_ACCOUNT, tableName=USER_ENTITY		\N	4.29.1	\N	\N	2731431181
18.0.0-10625-IDX_ADMIN_EVENT_TIME	keycloak	META-INF/jpa-changelog-18.0.0.xml	2026-06-29 11:10:34.193117	106	EXECUTED	9:015479dbd691d9cc8669282f4828c41d	createIndex indexName=IDX_ADMIN_EVENT_TIME, tableName=ADMIN_EVENT_ENTITY		\N	4.29.1	\N	\N	2731431181
18.0.15-30992-index-consent	keycloak	META-INF/jpa-changelog-18.0.15.xml	2026-06-29 11:10:34.217176	107	EXECUTED	9:80071ede7a05604b1f4906f3bf3b00f0	createIndex indexName=IDX_USCONSENT_SCOPE_ID, tableName=USER_CONSENT_CLIENT_SCOPE		\N	4.29.1	\N	\N	2731431181
19.0.0-10135	keycloak	META-INF/jpa-changelog-19.0.0.xml	2026-06-29 11:10:34.21973	108	EXECUTED	9:9518e495fdd22f78ad6425cc30630221	customChange		\N	4.29.1	\N	\N	2731431181
20.0.0-12964-supported-dbs	keycloak	META-INF/jpa-changelog-20.0.0.xml	2026-06-29 11:10:34.241281	109	EXECUTED	9:e5f243877199fd96bcc842f27a1656ac	createIndex indexName=IDX_GROUP_ATT_BY_NAME_VALUE, tableName=GROUP_ATTRIBUTE		\N	4.29.1	\N	\N	2731431181
20.0.0-12964-unsupported-dbs	keycloak	META-INF/jpa-changelog-20.0.0.xml	2026-06-29 11:10:34.242262	110	MARK_RAN	9:1a6fcaa85e20bdeae0a9ce49b41946a5	createIndex indexName=IDX_GROUP_ATT_BY_NAME_VALUE, tableName=GROUP_ATTRIBUTE		\N	4.29.1	\N	\N	2731431181
client-attributes-string-accomodation-fixed	keycloak	META-INF/jpa-changelog-20.0.0.xml	2026-06-29 11:10:34.245978	111	EXECUTED	9:3f332e13e90739ed0c35b0b25b7822ca	addColumn tableName=CLIENT_ATTRIBUTES; update tableName=CLIENT_ATTRIBUTES; dropColumn columnName=VALUE, tableName=CLIENT_ATTRIBUTES; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
21.0.2-17277	keycloak	META-INF/jpa-changelog-21.0.2.xml	2026-06-29 11:10:34.248397	112	EXECUTED	9:7ee1f7a3fb8f5588f171fb9a6ab623c0	customChange		\N	4.29.1	\N	\N	2731431181
21.1.0-19404	keycloak	META-INF/jpa-changelog-21.1.0.xml	2026-06-29 11:10:34.258027	113	EXECUTED	9:3d7e830b52f33676b9d64f7f2b2ea634	modifyDataType columnName=DECISION_STRATEGY, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=LOGIC, tableName=RESOURCE_SERVER_POLICY; modifyDataType columnName=POLICY_ENFORCE_MODE, tableName=RESOURCE_SERVER		\N	4.29.1	\N	\N	2731431181
21.1.0-19404-2	keycloak	META-INF/jpa-changelog-21.1.0.xml	2026-06-29 11:10:34.259757	114	MARK_RAN	9:627d032e3ef2c06c0e1f73d2ae25c26c	addColumn tableName=RESOURCE_SERVER_POLICY; update tableName=RESOURCE_SERVER_POLICY; dropColumn columnName=DECISION_STRATEGY, tableName=RESOURCE_SERVER_POLICY; renameColumn newColumnName=DECISION_STRATEGY, oldColumnName=DECISION_STRATEGY_NEW, tabl...		\N	4.29.1	\N	\N	2731431181
22.0.0-17484-updated	keycloak	META-INF/jpa-changelog-22.0.0.xml	2026-06-29 11:10:34.262305	115	EXECUTED	9:90af0bfd30cafc17b9f4d6eccd92b8b3	customChange		\N	4.29.1	\N	\N	2731431181
22.0.5-24031	keycloak	META-INF/jpa-changelog-22.0.0.xml	2026-06-29 11:10:34.26293	116	MARK_RAN	9:a60d2d7b315ec2d3eba9e2f145f9df28	customChange		\N	4.29.1	\N	\N	2731431181
23.0.0-12062	keycloak	META-INF/jpa-changelog-23.0.0.xml	2026-06-29 11:10:34.265985	117	EXECUTED	9:2168fbe728fec46ae9baf15bf80927b8	addColumn tableName=COMPONENT_CONFIG; update tableName=COMPONENT_CONFIG; dropColumn columnName=VALUE, tableName=COMPONENT_CONFIG; renameColumn newColumnName=VALUE, oldColumnName=VALUE_NEW, tableName=COMPONENT_CONFIG		\N	4.29.1	\N	\N	2731431181
23.0.0-17258	keycloak	META-INF/jpa-changelog-23.0.0.xml	2026-06-29 11:10:34.267391	118	EXECUTED	9:36506d679a83bbfda85a27ea1864dca8	addColumn tableName=EVENT_ENTITY		\N	4.29.1	\N	\N	2731431181
24.0.0-9758	keycloak	META-INF/jpa-changelog-24.0.0.xml	2026-06-29 11:10:34.342131	119	EXECUTED	9:502c557a5189f600f0f445a9b49ebbce	addColumn tableName=USER_ATTRIBUTE; addColumn tableName=FED_USER_ATTRIBUTE; createIndex indexName=USER_ATTR_LONG_VALUES, tableName=USER_ATTRIBUTE; createIndex indexName=FED_USER_ATTR_LONG_VALUES, tableName=FED_USER_ATTRIBUTE; createIndex indexName...		\N	4.29.1	\N	\N	2731431181
24.0.0-9758-2	keycloak	META-INF/jpa-changelog-24.0.0.xml	2026-06-29 11:10:34.344134	120	EXECUTED	9:bf0fdee10afdf597a987adbf291db7b2	customChange		\N	4.29.1	\N	\N	2731431181
24.0.0-26618-drop-index-if-present	keycloak	META-INF/jpa-changelog-24.0.0.xml	2026-06-29 11:10:34.346445	121	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
24.0.0-26618-reindex	keycloak	META-INF/jpa-changelog-24.0.0.xml	2026-06-29 11:10:34.364944	122	EXECUTED	9:08707c0f0db1cef6b352db03a60edc7f	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
24.0.2-27228	keycloak	META-INF/jpa-changelog-24.0.2.xml	2026-06-29 11:10:34.368587	123	EXECUTED	9:eaee11f6b8aa25d2cc6a84fb86fc6238	customChange		\N	4.29.1	\N	\N	2731431181
24.0.2-27967-drop-index-if-present	keycloak	META-INF/jpa-changelog-24.0.2.xml	2026-06-29 11:10:34.369684	124	MARK_RAN	9:04baaf56c116ed19951cbc2cca584022	dropIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
24.0.2-27967-reindex	keycloak	META-INF/jpa-changelog-24.0.2.xml	2026-06-29 11:10:34.371247	125	MARK_RAN	9:d3d977031d431db16e2c181ce49d73e9	createIndex indexName=IDX_CLIENT_ATT_BY_NAME_VALUE, tableName=CLIENT_ATTRIBUTES		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-tables	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.374237	126	EXECUTED	9:deda2df035df23388af95bbd36c17cef	addColumn tableName=OFFLINE_USER_SESSION; addColumn tableName=OFFLINE_CLIENT_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-creation	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.393879	127	EXECUTED	9:3e96709818458ae49f3c679ae58d263a	createIndex indexName=IDX_OFFLINE_USS_BY_LAST_SESSION_REFRESH, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-cleanup-uss-createdon	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.438385	128	EXECUTED	9:78ab4fc129ed5e8265dbcc3485fba92f	dropIndex indexName=IDX_OFFLINE_USS_CREATEDON, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-cleanup-uss-preload	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.479047	129	EXECUTED	9:de5f7c1f7e10994ed8b62e621d20eaab	dropIndex indexName=IDX_OFFLINE_USS_PRELOAD, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-cleanup-uss-by-usersess	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.517741	130	EXECUTED	9:6eee220d024e38e89c799417ec33667f	dropIndex indexName=IDX_OFFLINE_USS_BY_USERSESS, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-cleanup-css-preload	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.554783	131	EXECUTED	9:5411d2fb2891d3e8d63ddb55dfa3c0c9	dropIndex indexName=IDX_OFFLINE_CSS_PRELOAD, tableName=OFFLINE_CLIENT_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-2-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.555881	132	MARK_RAN	9:b7ef76036d3126bb83c2423bf4d449d6	createIndex indexName=IDX_OFFLINE_USS_BY_BROKER_SESSION_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-28265-index-2-not-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.579451	133	EXECUTED	9:23396cf51ab8bc1ae6f0cac7f9f6fcf7	createIndex indexName=IDX_OFFLINE_USS_BY_BROKER_SESSION_ID, tableName=OFFLINE_USER_SESSION		\N	4.29.1	\N	\N	2731431181
25.0.0-org	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.589211	134	EXECUTED	9:5c859965c2c9b9c72136c360649af157	createTable tableName=ORG; addUniqueConstraint constraintName=UK_ORG_NAME, tableName=ORG; addUniqueConstraint constraintName=UK_ORG_GROUP, tableName=ORG; createTable tableName=ORG_DOMAIN		\N	4.29.1	\N	\N	2731431181
unique-consentuser	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.595943	135	EXECUTED	9:5857626a2ea8767e9a6c66bf3a2cb32f	customChange; dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_LOCAL_CONSENT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_EXTERNAL_CONSENT, tableName=...		\N	4.29.1	\N	\N	2731431181
unique-consentuser-mysql	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.596794	136	MARK_RAN	9:b79478aad5adaa1bc428e31563f55e8e	customChange; dropUniqueConstraint constraintName=UK_JKUWUVD56ONTGSUHOGM8UEWRT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_LOCAL_CONSENT, tableName=USER_CONSENT; addUniqueConstraint constraintName=UK_EXTERNAL_CONSENT, tableName=...		\N	4.29.1	\N	\N	2731431181
25.0.0-28861-index-creation	keycloak	META-INF/jpa-changelog-25.0.0.xml	2026-06-29 11:10:34.636819	137	EXECUTED	9:b9acb58ac958d9ada0fe12a5d4794ab1	createIndex indexName=IDX_PERM_TICKET_REQUESTER, tableName=RESOURCE_SERVER_PERM_TICKET; createIndex indexName=IDX_PERM_TICKET_OWNER, tableName=RESOURCE_SERVER_PERM_TICKET		\N	4.29.1	\N	\N	2731431181
26.0.0-org-alias	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.640607	138	EXECUTED	9:6ef7d63e4412b3c2d66ed179159886a4	addColumn tableName=ORG; update tableName=ORG; addNotNullConstraint columnName=ALIAS, tableName=ORG; addUniqueConstraint constraintName=UK_ORG_ALIAS, tableName=ORG		\N	4.29.1	\N	\N	2731431181
26.0.0-org-group	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.643994	139	EXECUTED	9:da8e8087d80ef2ace4f89d8c5b9ca223	addColumn tableName=KEYCLOAK_GROUP; update tableName=KEYCLOAK_GROUP; addNotNullConstraint columnName=TYPE, tableName=KEYCLOAK_GROUP; customChange		\N	4.29.1	\N	\N	2731431181
26.0.0-org-indexes	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.663897	140	EXECUTED	9:79b05dcd610a8c7f25ec05135eec0857	createIndex indexName=IDX_ORG_DOMAIN_ORG_ID, tableName=ORG_DOMAIN		\N	4.29.1	\N	\N	2731431181
26.0.0-org-group-membership	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.666474	141	EXECUTED	9:a6ace2ce583a421d89b01ba2a28dc2d4	addColumn tableName=USER_GROUP_MEMBERSHIP; update tableName=USER_GROUP_MEMBERSHIP; addNotNullConstraint columnName=MEMBERSHIP_TYPE, tableName=USER_GROUP_MEMBERSHIP		\N	4.29.1	\N	\N	2731431181
31296-persist-revoked-access-tokens	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.669923	142	EXECUTED	9:64ef94489d42a358e8304b0e245f0ed4	createTable tableName=REVOKED_TOKEN; addPrimaryKey constraintName=CONSTRAINT_RT, tableName=REVOKED_TOKEN		\N	4.29.1	\N	\N	2731431181
31725-index-persist-revoked-access-tokens	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.690593	143	EXECUTED	9:b994246ec2bf7c94da881e1d28782c7b	createIndex indexName=IDX_REV_TOKEN_ON_EXPIRE, tableName=REVOKED_TOKEN		\N	4.29.1	\N	\N	2731431181
26.0.0-idps-for-login	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.740452	144	EXECUTED	9:51f5fffadf986983d4bd59582c6c1604	addColumn tableName=IDENTITY_PROVIDER; createIndex indexName=IDX_IDP_REALM_ORG, tableName=IDENTITY_PROVIDER; createIndex indexName=IDX_IDP_FOR_LOGIN, tableName=IDENTITY_PROVIDER; customChange		\N	4.29.1	\N	\N	2731431181
26.0.0-32583-drop-redundant-index-on-client-session	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.774814	145	EXECUTED	9:24972d83bf27317a055d234187bb4af9	dropIndex indexName=IDX_US_SESS_ID_ON_CL_SESS, tableName=OFFLINE_CLIENT_SESSION		\N	4.29.1	\N	\N	2731431181
26.0.0.32582-remove-tables-user-session-user-session-note-and-client-session	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.7813	146	EXECUTED	9:febdc0f47f2ed241c59e60f58c3ceea5	dropTable tableName=CLIENT_SESSION_ROLE; dropTable tableName=CLIENT_SESSION_NOTE; dropTable tableName=CLIENT_SESSION_PROT_MAPPER; dropTable tableName=CLIENT_SESSION_AUTH_STATUS; dropTable tableName=CLIENT_USER_SESSION_NOTE; dropTable tableName=CLI...		\N	4.29.1	\N	\N	2731431181
26.0.0-33201-org-redirect-url	keycloak	META-INF/jpa-changelog-26.0.0.xml	2026-06-29 11:10:34.782947	147	EXECUTED	9:4d0e22b0ac68ebe9794fa9cb752ea660	addColumn tableName=ORG		\N	4.29.1	\N	\N	2731431181
26.0.6-34013	keycloak	META-INF/jpa-changelog-26.0.6.xml	2026-06-29 11:10:34.786309	148	EXECUTED	9:e6b686a15759aef99a6d758a5c4c6a26	addColumn tableName=ADMIN_EVENT_ENTITY		\N	4.29.1	\N	\N	2731431181
\.


--
-- Data for Name: databasechangeloglock; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.databasechangeloglock (id, locked, lockgranted, lockedby) FROM stdin;
1	f	\N	\N
1000	f	\N	\N
\.


--
-- Data for Name: default_client_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.default_client_scope (realm_id, scope_id, default_scope) FROM stdin;
259bfe83-5374-4ae7-bb38-5464d0d5bc75	17c8a13e-ea53-420e-b0cd-1b251905e541	f
259bfe83-5374-4ae7-bb38-5464d0d5bc75	ca67e32d-e6cb-47d1-b92b-e6134d930814	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	c595b026-4f21-49a3-97c3-5dd334ad50e8	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	a5930a85-5391-46e7-946b-887c57753f84	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	50ea777d-bde5-4b4b-a26d-ba584c7fea16	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	ad2ee486-c593-4dbd-b090-74d53c2c21f5	f
259bfe83-5374-4ae7-bb38-5464d0d5bc75	466ce05b-3dc9-4495-af1e-fd319ae830bb	f
259bfe83-5374-4ae7-bb38-5464d0d5bc75	bc484b1a-c523-4e3f-a799-1c8ecfec14f1	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	52461eff-b39c-404b-9be3-1b2bee4b5af3	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	5dec384e-eccb-4f03-8078-81b35b6319d6	f
259bfe83-5374-4ae7-bb38-5464d0d5bc75	92c21645-ea9c-4433-abfd-c3323c4d7fc0	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	9282382a-6d8c-4529-b174-cb7b87276a67	t
259bfe83-5374-4ae7-bb38-5464d0d5bc75	b5d3388f-c9b1-4bf6-aef0-b942f0015427	f
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	e8b8adf3-2e48-406e-b03c-ee798b047ba6	f
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	2fd5f4d6-e0d0-434b-b647-b441196a86af	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	ca7f8f17-b3a2-4577-a6df-781b426a4052	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c86b7616-505a-47db-acef-beb669c1a5ec	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	89105071-a02d-448c-baaf-1197e4bb223d	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	61d5c7ba-888d-4678-9834-ece659a1dc75	f
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	195f10d2-6623-47d2-855e-7db6981c182d	f
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	a9f69da8-e105-499f-a44e-22ecd6043f95	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	5237f92a-3544-409d-a97e-551d5c8f27d7	f
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	c579e87e-c2cd-443c-b5bc-f6e35dd4b259	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f044e4ae-6203-4844-a78c-84fb60b0cd72	t
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b	f
83e82497-9a85-4600-9b3d-877c38c4ff59	b8a17460-770a-4aeb-aba3-c8e453f242f8	f
83e82497-9a85-4600-9b3d-877c38c4ff59	6c136e2a-5769-4566-9c62-568b1bf54a20	t
83e82497-9a85-4600-9b3d-877c38c4ff59	d28411e0-ff79-4ab9-8c96-302b3ae89996	t
83e82497-9a85-4600-9b3d-877c38c4ff59	9bf8f3c7-2599-44d8-93c8-375927f6a67a	t
83e82497-9a85-4600-9b3d-877c38c4ff59	604c75b6-95dd-45d1-af05-eb4c7ec83dc3	t
83e82497-9a85-4600-9b3d-877c38c4ff59	d9af3ac6-a7da-484a-97ce-5e1e25badc5f	f
83e82497-9a85-4600-9b3d-877c38c4ff59	38f5bf2d-dad0-449f-8d0c-438a4edb1540	f
83e82497-9a85-4600-9b3d-877c38c4ff59	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1	t
83e82497-9a85-4600-9b3d-877c38c4ff59	0d3d0b4f-8f6d-4b45-8914-b383d61ca069	t
83e82497-9a85-4600-9b3d-877c38c4ff59	735ea178-7454-4888-b330-21b6c6c200f9	f
83e82497-9a85-4600-9b3d-877c38c4ff59	29c4f76f-4ac1-42f3-8876-cd82b644632e	t
83e82497-9a85-4600-9b3d-877c38c4ff59	b8081550-653f-403f-8e2c-f5059baf4c44	t
83e82497-9a85-4600-9b3d-877c38c4ff59	b290d0cc-d348-4360-b168-8ac976c0085a	f
\.


--
-- Data for Name: event_entity; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.event_entity (id, client_id, details_json, error, ip_address, realm_id, session_id, event_time, type, user_id, details_json_long_value) FROM stdin;
\.


--
-- Data for Name: fed_user_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_attribute (id, name, user_id, realm_id, storage_provider_id, value, long_value_hash, long_value_hash_lower_case, long_value) FROM stdin;
\.


--
-- Data for Name: fed_user_consent; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_consent (id, client_id, user_id, realm_id, storage_provider_id, created_date, last_updated_date, client_storage_provider, external_client_id) FROM stdin;
\.


--
-- Data for Name: fed_user_consent_cl_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_consent_cl_scope (user_consent_id, scope_id) FROM stdin;
\.


--
-- Data for Name: fed_user_credential; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_credential (id, salt, type, created_date, user_id, realm_id, storage_provider_id, user_label, secret_data, credential_data, priority) FROM stdin;
\.


--
-- Data for Name: fed_user_group_membership; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_group_membership (group_id, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: fed_user_required_action; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_required_action (required_action, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: fed_user_role_mapping; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.fed_user_role_mapping (role_id, user_id, realm_id, storage_provider_id) FROM stdin;
\.


--
-- Data for Name: federated_identity; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.federated_identity (identity_provider, realm_id, federated_user_id, federated_username, token, user_id) FROM stdin;
\.


--
-- Data for Name: federated_user; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.federated_user (id, storage_provider_id, realm_id) FROM stdin;
\.


--
-- Data for Name: group_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.group_attribute (id, name, value, group_id) FROM stdin;
ac36d4d4-c7d4-41a0-87ce-e47680689375	tenant_id	28	e3895f1d-6beb-4fa2-a490-ffe89c238b09
5303be79-37a5-4864-b808-9060c106bd23	tenant_id	37	b78c37f3-35d3-4196-8087-d0050246de57
76dfe102-3dc0-4e09-987b-823234196922	tenant_id	4	062753ef-cfdc-49df-b1e2-7c0334294fc8
fdd3f3fe-ba48-4cc4-95bb-6647bb7a0d63	tenant_id	1	c8e2cf10-45e0-4ec8-acb5-5534a258d304
8302abaf-88d5-44e3-b1b4-5a0490e129f9	tenant_id	2	6c5a130b-6bbb-4245-be3d-5fe648d86e95
\.


--
-- Data for Name: group_role_mapping; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.group_role_mapping (role_id, group_id) FROM stdin;
\.


--
-- Data for Name: identity_provider; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.identity_provider (internal_id, enabled, provider_alias, provider_id, store_token, authenticate_by_default, realm_id, add_token_role, trust_email, first_broker_login_flow_id, post_broker_login_flow_id, provider_display_name, link_only, organization_id, hide_on_login) FROM stdin;
\.


--
-- Data for Name: identity_provider_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.identity_provider_config (identity_provider_id, value, name) FROM stdin;
\.


--
-- Data for Name: identity_provider_mapper; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.identity_provider_mapper (id, name, idp_alias, idp_mapper_name, realm_id) FROM stdin;
\.


--
-- Data for Name: idp_mapper_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.idp_mapper_config (idp_mapper_id, value, name) FROM stdin;
\.


--
-- Data for Name: keycloak_group; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.keycloak_group (id, name, parent_group, realm_id, type) FROM stdin;
e3895f1d-6beb-4fa2-a490-ffe89c238b09	College Seed Test	 	83e82497-9a85-4600-9b3d-877c38c4ff59	0
b78c37f3-35d3-4196-8087-d0050246de57	Collège L'Avenir — مدرسة المستقبل الإعدادية	 	83e82497-9a85-4600-9b3d-877c38c4ff59	0
062753ef-cfdc-49df-b1e2-7c0334294fc8	Collège Ibn Khaldoun — مدرسة ابن خلدون الإعدادية	 	83e82497-9a85-4600-9b3d-877c38c4ff59	0
6c5a130b-6bbb-4245-be3d-5fe648d86e95	College Carthage	 	83e82497-9a85-4600-9b3d-877c38c4ff59	0
c8e2cf10-45e0-4ec8-acb5-5534a258d304	College Ibn Khaldoun	 	83e82497-9a85-4600-9b3d-877c38c4ff59	0
\.


--
-- Data for Name: keycloak_role; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.keycloak_role (id, client_realm_constraint, client_role, description, name, realm_id, client, realm) FROM stdin;
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	${role_default-roles}	default-roles-master	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	\N
2171ed3f-e670-4b4a-838b-432d5d873e26	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	${role_create-realm}	create-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	\N
5650b984-c6a8-4360-86c3-164318770281	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	${role_admin}	admin	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	\N
d4d43531-81e6-4f6a-97f3-87e529d36d12	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_create-client}	create-client	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
7be257ff-ab7c-48d2-aeea-0fc0dbfc981d	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-realm}	view-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
93c49ab4-3529-46eb-bdb6-f6d7de94afa5	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-users}	view-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
4b7dbc6d-b751-4087-901a-9730b7b781d0	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-clients}	view-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
6e6cca0d-6f05-41a3-be63-6ca55c2e12b7	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-events}	view-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
e331969c-350d-4e69-9a2c-300c46d0a298	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-identity-providers}	view-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
8c32b201-e8a4-43d3-955b-eebf8738535d	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_view-authorization}	view-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
63483b8f-cccd-4b48-8ed2-aa786beff4bb	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-realm}	manage-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
66dfd9a8-b177-4a29-b4af-4d213318331c	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-users}	manage-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
d65b7342-c9da-43bb-b22b-37030678614f	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-clients}	manage-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
254ccdc9-7490-431e-b329-46cd5c1f79c7	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-events}	manage-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
5079921a-17af-4795-b6f4-46106425f2ef	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-identity-providers}	manage-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
c447f728-708d-4b79-8763-6cd9d3c449a1	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_manage-authorization}	manage-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
3f7388aa-f7c3-443c-9a00-b24ebda34da7	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_query-users}	query-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
ba4f0957-67fe-408d-9ad9-6dd369d8f3ca	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_query-clients}	query-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
feba3ab5-6a23-4de9-9429-74dbb268f3b1	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_query-realms}	query-realms	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
4fed1692-8eb5-4dcf-a4e7-cbf4e3c1b9a5	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_query-groups}	query-groups	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
55dec5cc-99e7-4f1d-8860-7e3d2cc835b7	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_view-profile}	view-profile	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
342d9096-b03f-4326-b1b4-b137b8d2e348	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_manage-account}	manage-account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
50ce9486-0143-44ba-8787-689bda4e765e	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_manage-account-links}	manage-account-links	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
d64fecbb-4fb3-4eeb-8270-f2c63decee30	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_view-applications}	view-applications	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
67accf88-2c27-4424-b20b-7baf94be3278	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_view-consent}	view-consent	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
73511c25-9e15-4e76-8388-280092428538	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_manage-consent}	manage-consent	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
74c27623-d249-453b-8038-6505f49139ba	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_view-groups}	view-groups	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
845bc8ae-3294-4175-b0ee-4e295f208b19	3d56e973-75fb-4d57-bb78-302af297fe9d	t	${role_delete-account}	delete-account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	3d56e973-75fb-4d57-bb78-302af297fe9d	\N
92f77537-f9f2-4d39-b999-e1a2727073e7	f4202c47-7418-4e21-b20a-b430e5a8e6e8	t	${role_read-token}	read-token	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f4202c47-7418-4e21-b20a-b430e5a8e6e8	\N
9949b2e8-b041-4b01-98c5-9285cc98c11d	21023f50-efbf-4680-94a0-dd36232f70b2	t	${role_impersonation}	impersonation	259bfe83-5374-4ae7-bb38-5464d0d5bc75	21023f50-efbf-4680-94a0-dd36232f70b2	\N
ae38ddb2-41d2-482e-85f2-edc9118bf2c2	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	${role_offline-access}	offline_access	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	\N
540cf173-5ebb-4d53-9f7a-1ea291dc6a22	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	${role_uma_authorization}	uma_authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	\N	\N
24addff9-15ea-411f-ab2a-e4f293f19fb2	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	t	\N	uma_protection	259bfe83-5374-4ae7-bb38-5464d0d5bc75	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
3209610d-c3e0-478f-9e69-5078e51fa2e2	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	${role_default-roles}	default-roles-platforme_school	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
2ce38629-16bb-4bb4-9182-5272b40bb885	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_create-client}	create-client	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
c3965494-5dbc-4b1c-9be1-c41fc9122f73	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-realm}	view-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
54acb520-38ee-4ab6-95a3-7a730a39afd0	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-users}	view-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
53511ecc-78ca-4e25-92a8-9484f673d0e2	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-clients}	view-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
a95e9ec9-c392-4f87-9768-9976a3a92596	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-events}	view-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
42cad862-cc0e-4e3c-b7a2-bed6a9a3378c	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-identity-providers}	view-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
1bc2dc23-f71d-4509-978c-eb450b459262	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_view-authorization}	view-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
5f3789ef-1c67-4a32-8b7c-95d1583f4ac2	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-realm}	manage-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
a0c4235c-5496-49bd-9c50-2f474626aeee	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-users}	manage-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
127d827c-262f-406b-9870-b06fb6113280	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-clients}	manage-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
9bb9ad58-4cdf-4ff4-aa7e-224b18234141	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-events}	manage-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
c879f248-26b8-421a-a723-e9c237041a3e	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-identity-providers}	manage-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
2813bcc4-715b-44e8-95af-436a1250ccd5	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_manage-authorization}	manage-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
c3aa9463-41dd-4248-966a-699f3ec33d49	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_query-users}	query-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
e3b24820-3ea8-47c6-b458-9784217700a7	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_query-clients}	query-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
b32263a1-b987-4b1a-8a1b-d5bd10efb8f2	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_query-realms}	query-realms	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
36a8c13a-0054-4059-9460-cb3d09dbb672	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_query-groups}	query-groups	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
998d8f55-103c-4486-b705-871e773b55e1	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_realm-admin}	realm-admin	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
9fd49d77-d3ff-4cb4-b6b3-5b2f13d76036	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_create-client}	create-client	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
6557c8bf-04f1-4220-9281-198c9329f169	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-realm}	view-realm	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
869e58f7-8fe9-4381-bfd0-698985e90aa2	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-users}	view-users	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
20589b20-a192-4d45-b3ef-41fd67fd7295	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-clients}	view-clients	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
94b473fd-d393-44c2-8b7f-366727a2a820	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-events}	view-events	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
5f4ee40b-493a-4f4c-9e53-ff1a62f8a85a	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-identity-providers}	view-identity-providers	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
d6bd889a-4d60-4229-aa9d-786e4a21f40c	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_view-authorization}	view-authorization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
894a6f13-677e-4652-91f9-bd966c7ce297	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-realm}	manage-realm	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
8645a0c6-bac5-406b-a652-c11ed62d3218	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-users}	manage-users	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
3278fe4e-d205-45b6-bdb8-6c3ec40b8923	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-clients}	manage-clients	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
3fd8f936-4c6c-4d21-aa83-3b0073974531	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-events}	manage-events	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
f365ae32-689e-48f4-a98e-255ffbd2af42	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-identity-providers}	manage-identity-providers	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
2edd6133-604d-4155-b2ae-890e0d6f2860	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_manage-authorization}	manage-authorization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
0b4f93c2-795f-4b0f-b20d-0c4e2ec7c201	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_query-users}	query-users	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
789eb3b5-459a-4ac4-8f83-f48baf602a8a	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_query-clients}	query-clients	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
902b2b93-12bf-4e8f-bc9d-d0c7c2bb3d64	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_query-realms}	query-realms	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
0756779c-6ef0-42e3-bc70-0b2f8f718501	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_query-groups}	query-groups	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
85bb459f-b034-4640-9f9b-9c39572487ae	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_view-profile}	view-profile	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
ddc79483-a4aa-4928-b496-02a84d6ada27	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_manage-account}	manage-account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
dfdc1644-88f1-48cb-b39b-ae10abfa442b	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_manage-account-links}	manage-account-links	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
3db2edc3-b451-4117-b056-c55ce04ce8ac	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_view-applications}	view-applications	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
5cab47e8-934e-45f7-be05-2aa4232594e7	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_view-consent}	view-consent	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
9ae92762-5b92-4b52-a12f-d07905dc6c29	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_manage-consent}	manage-consent	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
b3b02a95-8f7d-43ca-9be9-56a5c07f65d8	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_view-groups}	view-groups	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
3c592b03-9416-4281-8c09-a68d455552ca	d022332e-3f8b-44dc-9e5c-c6751920ab3d	t	${role_delete-account}	delete-account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	d022332e-3f8b-44dc-9e5c-c6751920ab3d	\N
c0924d47-3f07-4732-a30e-6e61cf702b20	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	t	${role_impersonation}	impersonation	259bfe83-5374-4ae7-bb38-5464d0d5bc75	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	\N
0c700f4d-ab68-4735-8d42-8baeb8c2e925	950ca38d-f174-4392-adef-9153ec984d2b	t	${role_impersonation}	impersonation	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	950ca38d-f174-4392-adef-9153ec984d2b	\N
54f3bcb1-2cd1-46d1-9940-9aa7c98e9ffa	9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	t	${role_read-token}	read-token	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	9ebc1670-346c-4dc9-aba6-d4ee8e0f975b	\N
1bb8eaf2-9cfb-445a-95bc-a5d892057ed7	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	${role_offline-access}	offline_access	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
5806c6da-b084-4503-aec9-d4a5ce4f08a5	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	${role_uma_authorization}	uma_authorization	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
be163c60-9450-4bd8-a2cd-f668d8c37e09	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	Super Admin de Platforme	PLATFORM_SUPER_ADMIN	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
512bcb3d-f391-4b20-979e-a3720d1bc3ed	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	Admin de l'etablissement	SCHOOL_ADMIN	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
b7940228-93ce-4cd9-9d71-cf6e8858dc17	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f		TEACHER	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
fb4e1d1e-e55d-4557-b0de-6c575bd238e7	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f		SURVEILLANT	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
95fdfd75-5bba-4aab-99de-98831f21de93	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f		PARENT	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
80052779-96c0-4ed9-88d8-7eace9cc5f08	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f		STUDENT	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	\N	\N
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	83e82497-9a85-4600-9b3d-877c38c4ff59	f	${role_default-roles}	default-roles-smartschool	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
2d07fbd8-cbb3-4075-aa70-4a607e7938dc	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_create-client}	create-client	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
ee875c0a-4272-4b47-93e2-44277db3e64f	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-realm}	view-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
51003cb2-1e24-402a-9b2c-cdd1afacec35	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-users}	view-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
944652cf-b72b-471b-a121-03167a58bf31	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-clients}	view-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
17b4a453-0fea-4632-9b0f-36b404f32816	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-events}	view-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
67c63331-f681-475f-aa62-179f3fc777d0	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-identity-providers}	view-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
bc7949b5-55de-4706-9f9c-580688005428	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_view-authorization}	view-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
8f18f309-f30a-4d86-8a0b-05a6110b93df	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-realm}	manage-realm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
a69c37fc-c410-4232-83d0-cd125048d689	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-users}	manage-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
9e47ac0e-1eda-44e2-837a-b1f35e4aada6	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-clients}	manage-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
3b5c70c8-b7f5-4d3a-8021-d99be2422489	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-events}	manage-events	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
fa1169ad-28ff-4440-b1a3-ae08e46fd24c	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-identity-providers}	manage-identity-providers	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
3f0fc970-efc5-48f5-af6d-193a6f259a96	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_manage-authorization}	manage-authorization	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
5177f0e2-12ba-46d3-a4c7-463372f118fd	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_query-users}	query-users	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
b4dc668a-ec85-4800-a7bc-36457038a11f	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_query-clients}	query-clients	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
060c04fc-c720-46d4-a101-bdd75cbced63	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_query-realms}	query-realms	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
28cff196-e832-4605-be64-f45cdbef9602	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_query-groups}	query-groups	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
38b1e947-d49e-4967-b256-8ac381fc1148	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_realm-admin}	realm-admin	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
7d7239de-ea60-4394-8da8-cf567e212bf3	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_create-client}	create-client	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
49cf1aad-74e2-4050-9624-3986de286933	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-realm}	view-realm	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
aaf983d6-da6a-4032-a445-7155a42ab9c8	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-users}	view-users	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
e720a5dc-4c21-4769-9356-85cdede9aa24	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-clients}	view-clients	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
03bbb552-e779-4551-a135-58a183c4c62e	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-events}	view-events	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
d705c775-55ba-4631-8547-d815a1f71b40	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-identity-providers}	view-identity-providers	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
a4a23a85-c976-41ed-971e-3f49d302a6da	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_view-authorization}	view-authorization	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
99df3929-ad70-4232-93ce-34b18da7885a	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-realm}	manage-realm	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
fe043124-aa91-4aa0-9746-dc1c8c425535	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-users}	manage-users	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
8d17fb1a-6f0d-479e-9819-39e6f5fe813b	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-clients}	manage-clients	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
1f4c8819-29f4-483f-8dc9-fe3d771f9b52	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-events}	manage-events	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
9e16f1b0-e64c-48fa-a434-f0f650b967bb	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-identity-providers}	manage-identity-providers	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
04e14c7b-b467-4470-a787-76c69b7f0422	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_manage-authorization}	manage-authorization	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
71b2ddf7-0195-4f4a-88ec-d4dc4d34ec62	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_query-users}	query-users	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
df6e1469-c470-4bdc-b03c-1d3907762370	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_query-clients}	query-clients	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
7fc45597-87c7-439e-8db5-f69addde70e6	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_query-realms}	query-realms	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
0b9d741d-26f4-472b-9961-cd88381531a0	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_query-groups}	query-groups	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
2fa03de9-69c3-4a5a-a726-9f4ea5b2e086	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_view-profile}	view-profile	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
5f0fb361-0dea-4069-9142-70491c0ed68f	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_manage-account}	manage-account	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
94f2216e-bf26-4a8b-86d3-3d0c317238c2	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_manage-account-links}	manage-account-links	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
4a4076e3-4caa-40ba-977d-452f62919d38	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_view-applications}	view-applications	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
5d2c748b-955e-4b7f-94a3-a01bea39f538	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_view-consent}	view-consent	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
0c3d8eb5-2d3a-474c-8e4e-cdaf52cc3d3c	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_manage-consent}	manage-consent	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
743c5ae7-d2a2-4350-9c14-d4a66aad2752	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_view-groups}	view-groups	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
20004189-cbc6-4e22-9593-c2b41ff5e702	4a7c7844-cde9-4654-b214-b2bd003f2344	t	${role_delete-account}	delete-account	83e82497-9a85-4600-9b3d-877c38c4ff59	4a7c7844-cde9-4654-b214-b2bd003f2344	\N
24b21e80-6e97-4339-90bd-b4e23a094176	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	t	${role_impersonation}	impersonation	259bfe83-5374-4ae7-bb38-5464d0d5bc75	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	\N
59251041-9187-4239-9fc8-37a575a82b48	8184fc2e-49bc-4c82-a490-135cebf3e73c	t	${role_impersonation}	impersonation	83e82497-9a85-4600-9b3d-877c38c4ff59	8184fc2e-49bc-4c82-a490-135cebf3e73c	\N
08b422fd-eae6-4067-a220-bf66826493fd	97ba8722-5833-4ae0-bba0-a3359d112f8d	t	${role_read-token}	read-token	83e82497-9a85-4600-9b3d-877c38c4ff59	97ba8722-5833-4ae0-bba0-a3359d112f8d	\N
62846123-b3ae-4585-9974-8f1b618cc069	83e82497-9a85-4600-9b3d-877c38c4ff59	f	${role_offline-access}	offline_access	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
702023f8-f89c-4458-afc1-3d67540b89d6	83e82497-9a85-4600-9b3d-877c38c4ff59	f	${role_uma_authorization}	uma_authorization	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
f6c0b2dc-c757-4ef4-9e1d-a7ec9eef8717	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	PLATFORM_SUPER_ADMIN	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
ce68eb42-579b-4fee-9f03-cd19d8822458	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	SCHOOL_ADMIN	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
412b35ac-a689-4cb7-b871-a72d353c28e7	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	TEACHER	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
945735ea-5cc5-4fae-835b-3109128766b1	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	SURVEILLANT	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
340816ab-bfd0-4de6-901c-f6611298b1b7	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	PARENT	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
9dc91fe5-6f19-4199-b871-b6106af14248	83e82497-9a85-4600-9b3d-877c38c4ff59	f	\N	STUDENT	83e82497-9a85-4600-9b3d-877c38c4ff59	\N	\N
\.


--
-- Data for Name: migration_model; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.migration_model (id, version, update_time) FROM stdin;
bvreq	26.0.8	1782731435
\.


--
-- Data for Name: offline_client_session; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.offline_client_session (user_session_id, client_id, offline_flag, "timestamp", data, client_storage_provider, external_client_id, version) FROM stdin;
be49e913-e5a8-4e8b-a3b5-22f91ebfca85	2e5dede9-f6b2-489c-8c2b-44ca02f30878	0	1789079003	{"authMethod":"openid-connect","notes":{"clientId":"2e5dede9-f6b2-489c-8c2b-44ca02f30878","scope":"openid","userSessionStartedAt":"1789079003","iss":"http://localhost:8081/realms/smartschool","startedAt":"1789079003","level-of-authentication":"-1"}}	local	local	0
e475f7ed-af1f-4b11-abe3-34400b8c7bbf	2e5dede9-f6b2-489c-8c2b-44ca02f30878	0	1789079365	{"authMethod":"openid-connect","notes":{"clientId":"2e5dede9-f6b2-489c-8c2b-44ca02f30878","scope":"openid","userSessionStartedAt":"1789077028","iss":"http://localhost:8081/realms/smartschool","startedAt":"1789077028","level-of-authentication":"-1"}}	local	local	6
4b39df80-9e4b-45d6-a242-546a4767f0e2	2e5dede9-f6b2-489c-8c2b-44ca02f30878	0	1789079396	{"authMethod":"openid-connect","notes":{"clientId":"2e5dede9-f6b2-489c-8c2b-44ca02f30878","scope":"openid","userSessionStartedAt":"1789074199","iss":"http://localhost:8081/realms/smartschool","startedAt":"1789074199","level-of-authentication":"-1"}}	local	local	24
\.


--
-- Data for Name: offline_user_session; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.offline_user_session (user_session_id, user_id, realm_id, created_on, offline_flag, data, last_session_refresh, broker_session_id, version) FROM stdin;
be49e913-e5a8-4e8b-a3b5-22f91ebfca85	1177d06d-97dc-4483-a8a7-ed4819b4a450	83e82497-9a85-4600-9b3d-877c38c4ff59	1789079003	0	{"ipAddress":"192.168.0.155","authMethod":"openid-connect","rememberMe":false,"started":0,"notes":{"KC_DEVICE_NOTE":"eyJpcEFkZHJlc3MiOiIxOTIuMTY4LjAuMTU1Iiwib3MiOiJPdGhlciIsIm9zVmVyc2lvbiI6IlVua25vd24iLCJicm93c2VyIjoib2todHRwLzQuMTIuMCIsImRldmljZSI6Ik90aGVyIiwibGFzdEFjY2VzcyI6MCwibW9iaWxlIjpmYWxzZX0=","authenticators-completed":"{\\"4be916a7-e03d-450e-997a-29c29a01add2\\":1789079003,\\"e4124050-670c-4d51-857a-0b1feb4eb79f\\":1789079003}"},"state":"LOGGED_IN"}	1789079003	\N	0
e475f7ed-af1f-4b11-abe3-34400b8c7bbf	fc4af818-eaaa-493d-9c30-e0fda104ca78	83e82497-9a85-4600-9b3d-877c38c4ff59	1789077028	0	{"ipAddress":"172.20.0.1","authMethod":"openid-connect","rememberMe":false,"started":0,"notes":{"KC_DEVICE_NOTE":"eyJpcEFkZHJlc3MiOiIxNzIuMjAuMC4xIiwib3MiOiJVYnVudHUiLCJvc1ZlcnNpb24iOiJVbmtub3duIiwiYnJvd3NlciI6IkZpcmVmb3gvMTU1LjAiLCJkZXZpY2UiOiJPdGhlciIsImxhc3RBY2Nlc3MiOjAsIm1vYmlsZSI6ZmFsc2V9","authenticators-completed":"{\\"4be916a7-e03d-450e-997a-29c29a01add2\\":1789077028,\\"e4124050-670c-4d51-857a-0b1feb4eb79f\\":1789077028}"},"state":"LOGGED_IN"}	1789079365	\N	6
4b39df80-9e4b-45d6-a242-546a4767f0e2	b0afe140-0b06-4065-9e40-bffe43e90b88	83e82497-9a85-4600-9b3d-877c38c4ff59	1789074199	0	{"ipAddress":"172.20.0.1","authMethod":"openid-connect","rememberMe":false,"started":0,"notes":{"KC_DEVICE_NOTE":"eyJpcEFkZHJlc3MiOiIxNzIuMjAuMC4xIiwib3MiOiJVYnVudHUiLCJvc1ZlcnNpb24iOiJVbmtub3duIiwiYnJvd3NlciI6IkZpcmVmb3gvMTU1LjAiLCJkZXZpY2UiOiJPdGhlciIsImxhc3RBY2Nlc3MiOjAsIm1vYmlsZSI6ZmFsc2V9","authenticators-completed":"{\\"4be916a7-e03d-450e-997a-29c29a01add2\\":1789074199,\\"e4124050-670c-4d51-857a-0b1feb4eb79f\\":1789074199}"},"state":"LOGGED_IN"}	1789079396	\N	24
\.


--
-- Data for Name: org; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.org (id, enabled, realm_id, group_id, name, description, alias, redirect_url) FROM stdin;
\.


--
-- Data for Name: org_domain; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.org_domain (id, name, verified, org_id) FROM stdin;
\.


--
-- Data for Name: policy_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.policy_config (policy_id, name, value) FROM stdin;
da878d11-f3ea-45ca-9891-e32fb38cfbd0	code	// by default, grants any permission associated with this policy\n$evaluation.grant();\n
5bb8041c-a9d7-4550-bb01-82fa40c4ac7a	defaultResourceType	urn:backend:resources:default
\.


--
-- Data for Name: protocol_mapper; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.protocol_mapper (id, name, protocol, protocol_mapper_name, client_id, client_scope_id) FROM stdin;
df4cbfe1-0913-402e-b149-81f308a47203	audience resolve	openid-connect	oidc-audience-resolve-mapper	1468f635-7aee-4d08-9633-86593942e889	\N
6c570e1e-3784-4beb-86ad-b15e0770327f	locale	openid-connect	oidc-usermodel-attribute-mapper	7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	\N
245e365b-f8c2-4b02-a8e7-6604a6856caf	role list	saml	saml-role-list-mapper	\N	ca67e32d-e6cb-47d1-b92b-e6134d930814
d1f19bec-5663-44ff-8618-1431b656f0b7	organization	saml	saml-organization-membership-mapper	\N	c595b026-4f21-49a3-97c3-5dd334ad50e8
e4d43fa8-b8ba-491e-b928-4fb8c9e5dc1a	full name	openid-connect	oidc-full-name-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
5b958d32-e561-47d8-a93b-25a539024ab6	family name	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
f0e29a98-845e-4ede-8daf-64463d3514e3	given name	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
a696b394-4edb-423f-ac3b-7e52d40f1da4	middle name	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
e0532cb0-728a-443b-8811-e4bf83514d17	nickname	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
f0494d7e-22d6-483e-9438-5dcc1af0406f	username	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
dd748144-63ea-4c3a-ba90-571975bcaa89	profile	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
9621040b-e1c1-412f-a4a1-a6307349c2ca	picture	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
6140e179-43fe-45a9-838d-29ebe89f7750	website	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
7c2e759c-a75d-496f-85b2-f5cec8248d5a	gender	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
1b342041-b541-403e-b85a-700a80d214d2	birthdate	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	zoneinfo	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	locale	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	updated at	openid-connect	oidc-usermodel-attribute-mapper	\N	a5930a85-5391-46e7-946b-887c57753f84
ad35af90-d093-4e69-a515-7895bc68d0f1	email	openid-connect	oidc-usermodel-attribute-mapper	\N	50ea777d-bde5-4b4b-a26d-ba584c7fea16
f5548d33-96e4-458e-b372-3d1b731be29c	email verified	openid-connect	oidc-usermodel-property-mapper	\N	50ea777d-bde5-4b4b-a26d-ba584c7fea16
d98cb296-c53a-4056-b9b9-2be70792a52a	address	openid-connect	oidc-address-mapper	\N	ad2ee486-c593-4dbd-b090-74d53c2c21f5
b79c49b9-2079-47f7-a8b7-9a070957f9b5	phone number	openid-connect	oidc-usermodel-attribute-mapper	\N	466ce05b-3dc9-4495-af1e-fd319ae830bb
d1e07393-ed19-4e05-8966-bd81000dfb0f	phone number verified	openid-connect	oidc-usermodel-attribute-mapper	\N	466ce05b-3dc9-4495-af1e-fd319ae830bb
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	realm roles	openid-connect	oidc-usermodel-realm-role-mapper	\N	bc484b1a-c523-4e3f-a799-1c8ecfec14f1
47ee09a7-3627-4548-a832-b6f930ec618a	client roles	openid-connect	oidc-usermodel-client-role-mapper	\N	bc484b1a-c523-4e3f-a799-1c8ecfec14f1
78366834-a9bc-4608-b784-80703ca00623	audience resolve	openid-connect	oidc-audience-resolve-mapper	\N	bc484b1a-c523-4e3f-a799-1c8ecfec14f1
1472a870-2dd4-4d80-9f00-3f3d512a0ae5	allowed web origins	openid-connect	oidc-allowed-origins-mapper	\N	52461eff-b39c-404b-9be3-1b2bee4b5af3
457bbd8d-ed36-48fe-9dc8-d7298296f354	upn	openid-connect	oidc-usermodel-attribute-mapper	\N	5dec384e-eccb-4f03-8078-81b35b6319d6
05aa0f4b-3a37-4b95-94e2-b9504594aabd	groups	openid-connect	oidc-usermodel-realm-role-mapper	\N	5dec384e-eccb-4f03-8078-81b35b6319d6
b342d497-b33e-4421-b7e5-e12619c1a5ed	acr loa level	openid-connect	oidc-acr-mapper	\N	92c21645-ea9c-4433-abfd-c3323c4d7fc0
4a220745-a27d-4592-a729-b3df681f1d04	auth_time	openid-connect	oidc-usersessionmodel-note-mapper	\N	9282382a-6d8c-4529-b174-cb7b87276a67
8506cf54-2b12-4fb3-932c-b10cd67fd4ac	sub	openid-connect	oidc-sub-mapper	\N	9282382a-6d8c-4529-b174-cb7b87276a67
1b16f1fe-6f3b-476b-a948-ecd43f290eef	organization	openid-connect	oidc-organization-membership-mapper	\N	b5d3388f-c9b1-4bf6-aef0-b942f0015427
91aa6a86-666f-4103-a047-d0746f80c286	Client ID	openid-connect	oidc-usersessionmodel-note-mapper	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
8a9254ae-bb6e-43d5-a45f-155351a9f639	Client Host	openid-connect	oidc-usersessionmodel-note-mapper	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
267e37b1-5575-45d7-96c3-d192d48aadd2	Client IP Address	openid-connect	oidc-usersessionmodel-note-mapper	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
ec282eee-e386-4fc6-a972-4b927f581ad3	audience resolve	openid-connect	oidc-audience-resolve-mapper	d2dfba85-0770-401f-9388-86bb979ab264	\N
0a099403-0cd3-401e-9743-8a45a301d8b1	role list	saml	saml-role-list-mapper	\N	2fd5f4d6-e0d0-434b-b647-b441196a86af
2dc19d5a-9202-4a0c-93a8-66fcc24792be	organization	saml	saml-organization-membership-mapper	\N	ca7f8f17-b3a2-4577-a6df-781b426a4052
0e2ece73-614f-4d70-8a95-f7000c3d7e32	full name	openid-connect	oidc-full-name-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
904b5766-f4af-4898-94b1-275dd2606a78	family name	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
5c600047-533b-42c3-a582-0502c93d4734	given name	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	middle name	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
5a34fd2f-c161-424d-83f6-569c37485b3b	nickname	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
542672d3-2591-447d-b56a-4c4a0ea5d048	username	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	profile	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
155edd6a-1da2-4339-84cb-536224c0a279	picture	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	website	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
36298f66-7fdb-489a-8a9d-b917d934b756	gender	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
66438034-4029-42fd-9cfa-2b58de7cfe7f	birthdate	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
c85b1178-4c69-46bb-b632-c45c5700f6d5	zoneinfo	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	locale	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
7a0253cf-9389-4df4-8003-c83fb96bea96	updated at	openid-connect	oidc-usermodel-attribute-mapper	\N	c86b7616-505a-47db-acef-beb669c1a5ec
88977265-3373-47ef-a417-f71b544a7a37	email	openid-connect	oidc-usermodel-attribute-mapper	\N	89105071-a02d-448c-baaf-1197e4bb223d
7a3987a5-5a44-42d1-8bec-d5a6752526f2	email verified	openid-connect	oidc-usermodel-property-mapper	\N	89105071-a02d-448c-baaf-1197e4bb223d
8a78d2d3-fe3c-421d-9388-0e167d68eecf	address	openid-connect	oidc-address-mapper	\N	61d5c7ba-888d-4678-9834-ece659a1dc75
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	phone number	openid-connect	oidc-usermodel-attribute-mapper	\N	195f10d2-6623-47d2-855e-7db6981c182d
94d23547-67d4-4530-9183-1ecb283eb941	phone number verified	openid-connect	oidc-usermodel-attribute-mapper	\N	195f10d2-6623-47d2-855e-7db6981c182d
a3892f72-a43e-473a-acca-a6f703d5ecd9	realm roles	openid-connect	oidc-usermodel-realm-role-mapper	\N	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1
31794e9d-c8c4-4781-85c3-9b2e299f5c03	client roles	openid-connect	oidc-usermodel-client-role-mapper	\N	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1
d884da86-671c-435b-b439-2415bd833069	audience resolve	openid-connect	oidc-audience-resolve-mapper	\N	c8da4b1b-9dec-42f6-acda-0c9ad9b581e1
e84da861-f977-4459-8902-fd045d9af55d	allowed web origins	openid-connect	oidc-allowed-origins-mapper	\N	a9f69da8-e105-499f-a44e-22ecd6043f95
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	upn	openid-connect	oidc-usermodel-attribute-mapper	\N	5237f92a-3544-409d-a97e-551d5c8f27d7
e3416dbb-0511-4715-aaa7-976684e46841	groups	openid-connect	oidc-usermodel-realm-role-mapper	\N	5237f92a-3544-409d-a97e-551d5c8f27d7
dc98e4bc-e696-456a-8f0c-45b3bd472cdb	acr loa level	openid-connect	oidc-acr-mapper	\N	c579e87e-c2cd-443c-b5bc-f6e35dd4b259
5f162570-5072-40ee-a473-ba2d07d65d30	auth_time	openid-connect	oidc-usersessionmodel-note-mapper	\N	f044e4ae-6203-4844-a78c-84fb60b0cd72
633cab6a-b13a-44fc-89b6-33ab87385df3	sub	openid-connect	oidc-sub-mapper	\N	f044e4ae-6203-4844-a78c-84fb60b0cd72
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	organization	openid-connect	oidc-organization-membership-mapper	\N	0d9bf56e-a7d2-4bb6-b68e-e05c9074ae7b
cdd62daa-e865-4cac-bb8f-61e6133a8c63	locale	openid-connect	oidc-usermodel-attribute-mapper	cbcbcbde-a715-4358-a3c3-7f027c4436b6	\N
a0df39a9-d33c-4f47-88e8-72f913dfc222	Client ID	openid-connect	oidc-usersessionmodel-note-mapper	2d441a2b-1316-4adc-b51a-8e2520fc33e6	\N
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	Client Host	openid-connect	oidc-usersessionmodel-note-mapper	2d441a2b-1316-4adc-b51a-8e2520fc33e6	\N
ffd7a743-377f-4348-a94f-72f013cbc6ff	Client IP Address	openid-connect	oidc-usersessionmodel-note-mapper	2d441a2b-1316-4adc-b51a-8e2520fc33e6	\N
34f0436f-0d21-4a11-87ca-cf0d4107f4af	tenant-id-mapper	openid-connect	oidc-group-membership-mapper	2d441a2b-1316-4adc-b51a-8e2520fc33e6	\N
d249a3f9-5f4a-43f4-bb2f-429e1ab2b633	audience resolve	openid-connect	oidc-audience-resolve-mapper	cba0a035-784d-4ec0-9c98-64f8bb89002b	\N
a8c7cb76-eb43-45ea-913f-a05b3b8a7707	role list	saml	saml-role-list-mapper	\N	6c136e2a-5769-4566-9c62-568b1bf54a20
916539f3-ad82-4b2e-b6dc-6c2b4bc67b13	organization	saml	saml-organization-membership-mapper	\N	d28411e0-ff79-4ab9-8c96-302b3ae89996
3636171f-ca8d-4cef-8f7a-96d4111898e1	full name	openid-connect	oidc-full-name-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
e00029b8-b027-4b72-b742-4237cf0c7a87	family name	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
46d1ca01-3aa9-462d-a617-074db6c38737	given name	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	middle name	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	nickname	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
c54b61ac-5d09-41ea-9fde-d16a2807608c	username	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	profile	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
a32c9b61-04cf-4736-925f-b73f6bfffeb8	picture	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	website	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
89019e02-d502-4286-90fe-b36eea42472e	gender	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	birthdate	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	zoneinfo	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	locale	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	updated at	openid-connect	oidc-usermodel-attribute-mapper	\N	9bf8f3c7-2599-44d8-93c8-375927f6a67a
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	email	openid-connect	oidc-usermodel-attribute-mapper	\N	604c75b6-95dd-45d1-af05-eb4c7ec83dc3
8f21d49b-91c8-4909-b539-f5521bf6234b	email verified	openid-connect	oidc-usermodel-property-mapper	\N	604c75b6-95dd-45d1-af05-eb4c7ec83dc3
46af36ba-8e32-477e-9c75-15c50ec25ff2	address	openid-connect	oidc-address-mapper	\N	d9af3ac6-a7da-484a-97ce-5e1e25badc5f
743e4d02-43d1-446c-ba80-e97a57962970	phone number	openid-connect	oidc-usermodel-attribute-mapper	\N	38f5bf2d-dad0-449f-8d0c-438a4edb1540
fd9ffc25-143c-4569-b59b-61b7d47461b6	phone number verified	openid-connect	oidc-usermodel-attribute-mapper	\N	38f5bf2d-dad0-449f-8d0c-438a4edb1540
4482779f-c2aa-4b40-9d72-ed0a271dbd00	realm roles	openid-connect	oidc-usermodel-realm-role-mapper	\N	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	client roles	openid-connect	oidc-usermodel-client-role-mapper	\N	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1
dfda790c-e7d7-48c6-a806-39595bbdcd95	audience resolve	openid-connect	oidc-audience-resolve-mapper	\N	9e5f1fa1-920e-4e38-846d-4a6f9c70b6c1
381f34b1-b471-44e1-ac96-4453a9b22034	allowed web origins	openid-connect	oidc-allowed-origins-mapper	\N	0d3d0b4f-8f6d-4b45-8914-b383d61ca069
74a56ac8-acbc-4307-9555-4def3d2d04ca	upn	openid-connect	oidc-usermodel-attribute-mapper	\N	735ea178-7454-4888-b330-21b6c6c200f9
9bfad321-7544-4a01-a3fd-3278dacb4b0a	groups	openid-connect	oidc-usermodel-realm-role-mapper	\N	735ea178-7454-4888-b330-21b6c6c200f9
335b2043-1f16-44d0-a0ae-64e95c8ca0ea	acr loa level	openid-connect	oidc-acr-mapper	\N	29c4f76f-4ac1-42f3-8876-cd82b644632e
39f38d03-5e6e-468e-8ad1-3b2ee314639d	auth_time	openid-connect	oidc-usersessionmodel-note-mapper	\N	b8081550-653f-403f-8e2c-f5059baf4c44
e243b7dd-8eae-40ef-844f-0c82f610d33a	sub	openid-connect	oidc-sub-mapper	\N	b8081550-653f-403f-8e2c-f5059baf4c44
333d0313-8a63-4887-b996-fbd4dd6e5c89	organization	openid-connect	oidc-organization-membership-mapper	\N	b290d0cc-d348-4360-b168-8ac976c0085a
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	locale	openid-connect	oidc-usermodel-attribute-mapper	d5442a0f-3191-407b-befa-391aaa28773d	\N
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	Client ID	openid-connect	oidc-usersessionmodel-note-mapper	5907c420-e9c0-4884-85ed-a753a9be228e	\N
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	Client Host	openid-connect	oidc-usersessionmodel-note-mapper	5907c420-e9c0-4884-85ed-a753a9be228e	\N
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	Client IP Address	openid-connect	oidc-usersessionmodel-note-mapper	5907c420-e9c0-4884-85ed-a753a9be228e	\N
78937947-ad50-42f2-963d-b4de649ebfd6	tenant_id	openid-connect	oidc-usermodel-attribute-mapper	2e5dede9-f6b2-489c-8c2b-44ca02f30878	\N
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	must_change_password	openid-connect	oidc-usermodel-attribute-mapper	2e5dede9-f6b2-489c-8c2b-44ca02f30878	\N
\.


--
-- Data for Name: protocol_mapper_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.protocol_mapper_config (protocol_mapper_id, value, name) FROM stdin;
6c570e1e-3784-4beb-86ad-b15e0770327f	true	introspection.token.claim
6c570e1e-3784-4beb-86ad-b15e0770327f	true	userinfo.token.claim
6c570e1e-3784-4beb-86ad-b15e0770327f	locale	user.attribute
6c570e1e-3784-4beb-86ad-b15e0770327f	true	id.token.claim
6c570e1e-3784-4beb-86ad-b15e0770327f	true	access.token.claim
6c570e1e-3784-4beb-86ad-b15e0770327f	locale	claim.name
6c570e1e-3784-4beb-86ad-b15e0770327f	String	jsonType.label
245e365b-f8c2-4b02-a8e7-6604a6856caf	false	single
245e365b-f8c2-4b02-a8e7-6604a6856caf	Basic	attribute.nameformat
245e365b-f8c2-4b02-a8e7-6604a6856caf	Role	attribute.name
1b342041-b541-403e-b85a-700a80d214d2	true	introspection.token.claim
1b342041-b541-403e-b85a-700a80d214d2	true	userinfo.token.claim
1b342041-b541-403e-b85a-700a80d214d2	birthdate	user.attribute
1b342041-b541-403e-b85a-700a80d214d2	true	id.token.claim
1b342041-b541-403e-b85a-700a80d214d2	true	access.token.claim
1b342041-b541-403e-b85a-700a80d214d2	birthdate	claim.name
1b342041-b541-403e-b85a-700a80d214d2	String	jsonType.label
5b958d32-e561-47d8-a93b-25a539024ab6	true	introspection.token.claim
5b958d32-e561-47d8-a93b-25a539024ab6	true	userinfo.token.claim
5b958d32-e561-47d8-a93b-25a539024ab6	lastName	user.attribute
5b958d32-e561-47d8-a93b-25a539024ab6	true	id.token.claim
5b958d32-e561-47d8-a93b-25a539024ab6	true	access.token.claim
5b958d32-e561-47d8-a93b-25a539024ab6	family_name	claim.name
5b958d32-e561-47d8-a93b-25a539024ab6	String	jsonType.label
6140e179-43fe-45a9-838d-29ebe89f7750	true	introspection.token.claim
6140e179-43fe-45a9-838d-29ebe89f7750	true	userinfo.token.claim
6140e179-43fe-45a9-838d-29ebe89f7750	website	user.attribute
6140e179-43fe-45a9-838d-29ebe89f7750	true	id.token.claim
6140e179-43fe-45a9-838d-29ebe89f7750	true	access.token.claim
6140e179-43fe-45a9-838d-29ebe89f7750	website	claim.name
6140e179-43fe-45a9-838d-29ebe89f7750	String	jsonType.label
7c2e759c-a75d-496f-85b2-f5cec8248d5a	true	introspection.token.claim
7c2e759c-a75d-496f-85b2-f5cec8248d5a	true	userinfo.token.claim
7c2e759c-a75d-496f-85b2-f5cec8248d5a	gender	user.attribute
7c2e759c-a75d-496f-85b2-f5cec8248d5a	true	id.token.claim
7c2e759c-a75d-496f-85b2-f5cec8248d5a	true	access.token.claim
7c2e759c-a75d-496f-85b2-f5cec8248d5a	gender	claim.name
7c2e759c-a75d-496f-85b2-f5cec8248d5a	String	jsonType.label
9621040b-e1c1-412f-a4a1-a6307349c2ca	true	introspection.token.claim
9621040b-e1c1-412f-a4a1-a6307349c2ca	true	userinfo.token.claim
9621040b-e1c1-412f-a4a1-a6307349c2ca	picture	user.attribute
9621040b-e1c1-412f-a4a1-a6307349c2ca	true	id.token.claim
9621040b-e1c1-412f-a4a1-a6307349c2ca	true	access.token.claim
9621040b-e1c1-412f-a4a1-a6307349c2ca	picture	claim.name
9621040b-e1c1-412f-a4a1-a6307349c2ca	String	jsonType.label
a696b394-4edb-423f-ac3b-7e52d40f1da4	true	introspection.token.claim
a696b394-4edb-423f-ac3b-7e52d40f1da4	true	userinfo.token.claim
a696b394-4edb-423f-ac3b-7e52d40f1da4	middleName	user.attribute
a696b394-4edb-423f-ac3b-7e52d40f1da4	true	id.token.claim
a696b394-4edb-423f-ac3b-7e52d40f1da4	true	access.token.claim
a696b394-4edb-423f-ac3b-7e52d40f1da4	middle_name	claim.name
a696b394-4edb-423f-ac3b-7e52d40f1da4	String	jsonType.label
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	true	introspection.token.claim
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	true	userinfo.token.claim
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	updatedAt	user.attribute
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	true	id.token.claim
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	true	access.token.claim
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	updated_at	claim.name
d1d31d57-ac6d-49a7-8e8b-3cd52b1fcb34	long	jsonType.label
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	true	introspection.token.claim
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	true	userinfo.token.claim
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	locale	user.attribute
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	true	id.token.claim
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	true	access.token.claim
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	locale	claim.name
d97fe6a6-9708-4078-8fc9-1c1c1c87e17b	String	jsonType.label
dd748144-63ea-4c3a-ba90-571975bcaa89	true	introspection.token.claim
dd748144-63ea-4c3a-ba90-571975bcaa89	true	userinfo.token.claim
dd748144-63ea-4c3a-ba90-571975bcaa89	profile	user.attribute
dd748144-63ea-4c3a-ba90-571975bcaa89	true	id.token.claim
dd748144-63ea-4c3a-ba90-571975bcaa89	true	access.token.claim
dd748144-63ea-4c3a-ba90-571975bcaa89	profile	claim.name
dd748144-63ea-4c3a-ba90-571975bcaa89	String	jsonType.label
e0532cb0-728a-443b-8811-e4bf83514d17	true	introspection.token.claim
e0532cb0-728a-443b-8811-e4bf83514d17	true	userinfo.token.claim
e0532cb0-728a-443b-8811-e4bf83514d17	nickname	user.attribute
e0532cb0-728a-443b-8811-e4bf83514d17	true	id.token.claim
e0532cb0-728a-443b-8811-e4bf83514d17	true	access.token.claim
e0532cb0-728a-443b-8811-e4bf83514d17	nickname	claim.name
e0532cb0-728a-443b-8811-e4bf83514d17	String	jsonType.label
e4d43fa8-b8ba-491e-b928-4fb8c9e5dc1a	true	introspection.token.claim
e4d43fa8-b8ba-491e-b928-4fb8c9e5dc1a	true	userinfo.token.claim
e4d43fa8-b8ba-491e-b928-4fb8c9e5dc1a	true	id.token.claim
e4d43fa8-b8ba-491e-b928-4fb8c9e5dc1a	true	access.token.claim
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	true	introspection.token.claim
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	true	userinfo.token.claim
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	zoneinfo	user.attribute
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	true	id.token.claim
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	true	access.token.claim
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	zoneinfo	claim.name
ef02eb0c-70e5-45c2-92fa-bb2bb5143ecb	String	jsonType.label
f0494d7e-22d6-483e-9438-5dcc1af0406f	true	introspection.token.claim
f0494d7e-22d6-483e-9438-5dcc1af0406f	true	userinfo.token.claim
f0494d7e-22d6-483e-9438-5dcc1af0406f	username	user.attribute
f0494d7e-22d6-483e-9438-5dcc1af0406f	true	id.token.claim
f0494d7e-22d6-483e-9438-5dcc1af0406f	true	access.token.claim
f0494d7e-22d6-483e-9438-5dcc1af0406f	preferred_username	claim.name
f0494d7e-22d6-483e-9438-5dcc1af0406f	String	jsonType.label
f0e29a98-845e-4ede-8daf-64463d3514e3	true	introspection.token.claim
f0e29a98-845e-4ede-8daf-64463d3514e3	true	userinfo.token.claim
f0e29a98-845e-4ede-8daf-64463d3514e3	firstName	user.attribute
f0e29a98-845e-4ede-8daf-64463d3514e3	true	id.token.claim
f0e29a98-845e-4ede-8daf-64463d3514e3	true	access.token.claim
f0e29a98-845e-4ede-8daf-64463d3514e3	given_name	claim.name
f0e29a98-845e-4ede-8daf-64463d3514e3	String	jsonType.label
ad35af90-d093-4e69-a515-7895bc68d0f1	true	introspection.token.claim
ad35af90-d093-4e69-a515-7895bc68d0f1	true	userinfo.token.claim
ad35af90-d093-4e69-a515-7895bc68d0f1	email	user.attribute
ad35af90-d093-4e69-a515-7895bc68d0f1	true	id.token.claim
ad35af90-d093-4e69-a515-7895bc68d0f1	true	access.token.claim
ad35af90-d093-4e69-a515-7895bc68d0f1	email	claim.name
ad35af90-d093-4e69-a515-7895bc68d0f1	String	jsonType.label
f5548d33-96e4-458e-b372-3d1b731be29c	true	introspection.token.claim
f5548d33-96e4-458e-b372-3d1b731be29c	true	userinfo.token.claim
f5548d33-96e4-458e-b372-3d1b731be29c	emailVerified	user.attribute
f5548d33-96e4-458e-b372-3d1b731be29c	true	id.token.claim
f5548d33-96e4-458e-b372-3d1b731be29c	true	access.token.claim
f5548d33-96e4-458e-b372-3d1b731be29c	email_verified	claim.name
f5548d33-96e4-458e-b372-3d1b731be29c	boolean	jsonType.label
d98cb296-c53a-4056-b9b9-2be70792a52a	formatted	user.attribute.formatted
d98cb296-c53a-4056-b9b9-2be70792a52a	country	user.attribute.country
d98cb296-c53a-4056-b9b9-2be70792a52a	true	introspection.token.claim
d98cb296-c53a-4056-b9b9-2be70792a52a	postal_code	user.attribute.postal_code
d98cb296-c53a-4056-b9b9-2be70792a52a	true	userinfo.token.claim
d98cb296-c53a-4056-b9b9-2be70792a52a	street	user.attribute.street
d98cb296-c53a-4056-b9b9-2be70792a52a	true	id.token.claim
d98cb296-c53a-4056-b9b9-2be70792a52a	region	user.attribute.region
d98cb296-c53a-4056-b9b9-2be70792a52a	true	access.token.claim
d98cb296-c53a-4056-b9b9-2be70792a52a	locality	user.attribute.locality
b79c49b9-2079-47f7-a8b7-9a070957f9b5	true	introspection.token.claim
b79c49b9-2079-47f7-a8b7-9a070957f9b5	true	userinfo.token.claim
b79c49b9-2079-47f7-a8b7-9a070957f9b5	phoneNumber	user.attribute
b79c49b9-2079-47f7-a8b7-9a070957f9b5	true	id.token.claim
b79c49b9-2079-47f7-a8b7-9a070957f9b5	true	access.token.claim
b79c49b9-2079-47f7-a8b7-9a070957f9b5	phone_number	claim.name
b79c49b9-2079-47f7-a8b7-9a070957f9b5	String	jsonType.label
d1e07393-ed19-4e05-8966-bd81000dfb0f	true	introspection.token.claim
d1e07393-ed19-4e05-8966-bd81000dfb0f	true	userinfo.token.claim
d1e07393-ed19-4e05-8966-bd81000dfb0f	phoneNumberVerified	user.attribute
d1e07393-ed19-4e05-8966-bd81000dfb0f	true	id.token.claim
d1e07393-ed19-4e05-8966-bd81000dfb0f	true	access.token.claim
d1e07393-ed19-4e05-8966-bd81000dfb0f	phone_number_verified	claim.name
d1e07393-ed19-4e05-8966-bd81000dfb0f	boolean	jsonType.label
47ee09a7-3627-4548-a832-b6f930ec618a	true	introspection.token.claim
47ee09a7-3627-4548-a832-b6f930ec618a	true	multivalued
47ee09a7-3627-4548-a832-b6f930ec618a	foo	user.attribute
47ee09a7-3627-4548-a832-b6f930ec618a	true	access.token.claim
47ee09a7-3627-4548-a832-b6f930ec618a	resource_access.${client_id}.roles	claim.name
47ee09a7-3627-4548-a832-b6f930ec618a	String	jsonType.label
78366834-a9bc-4608-b784-80703ca00623	true	introspection.token.claim
78366834-a9bc-4608-b784-80703ca00623	true	access.token.claim
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	true	introspection.token.claim
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	true	multivalued
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	foo	user.attribute
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	true	access.token.claim
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	realm_access.roles	claim.name
80bc4ca5-32df-4adb-815c-a4ed34cf08e6	String	jsonType.label
1472a870-2dd4-4d80-9f00-3f3d512a0ae5	true	introspection.token.claim
1472a870-2dd4-4d80-9f00-3f3d512a0ae5	true	access.token.claim
05aa0f4b-3a37-4b95-94e2-b9504594aabd	true	introspection.token.claim
05aa0f4b-3a37-4b95-94e2-b9504594aabd	true	multivalued
05aa0f4b-3a37-4b95-94e2-b9504594aabd	foo	user.attribute
05aa0f4b-3a37-4b95-94e2-b9504594aabd	true	id.token.claim
05aa0f4b-3a37-4b95-94e2-b9504594aabd	true	access.token.claim
05aa0f4b-3a37-4b95-94e2-b9504594aabd	groups	claim.name
05aa0f4b-3a37-4b95-94e2-b9504594aabd	String	jsonType.label
457bbd8d-ed36-48fe-9dc8-d7298296f354	true	introspection.token.claim
457bbd8d-ed36-48fe-9dc8-d7298296f354	true	userinfo.token.claim
457bbd8d-ed36-48fe-9dc8-d7298296f354	username	user.attribute
457bbd8d-ed36-48fe-9dc8-d7298296f354	true	id.token.claim
457bbd8d-ed36-48fe-9dc8-d7298296f354	true	access.token.claim
457bbd8d-ed36-48fe-9dc8-d7298296f354	upn	claim.name
457bbd8d-ed36-48fe-9dc8-d7298296f354	String	jsonType.label
b342d497-b33e-4421-b7e5-e12619c1a5ed	true	introspection.token.claim
b342d497-b33e-4421-b7e5-e12619c1a5ed	true	id.token.claim
b342d497-b33e-4421-b7e5-e12619c1a5ed	true	access.token.claim
4a220745-a27d-4592-a729-b3df681f1d04	AUTH_TIME	user.session.note
4a220745-a27d-4592-a729-b3df681f1d04	true	introspection.token.claim
4a220745-a27d-4592-a729-b3df681f1d04	true	id.token.claim
4a220745-a27d-4592-a729-b3df681f1d04	true	access.token.claim
4a220745-a27d-4592-a729-b3df681f1d04	auth_time	claim.name
4a220745-a27d-4592-a729-b3df681f1d04	long	jsonType.label
8506cf54-2b12-4fb3-932c-b10cd67fd4ac	true	introspection.token.claim
8506cf54-2b12-4fb3-932c-b10cd67fd4ac	true	access.token.claim
1b16f1fe-6f3b-476b-a948-ecd43f290eef	true	introspection.token.claim
1b16f1fe-6f3b-476b-a948-ecd43f290eef	true	multivalued
1b16f1fe-6f3b-476b-a948-ecd43f290eef	true	id.token.claim
1b16f1fe-6f3b-476b-a948-ecd43f290eef	true	access.token.claim
1b16f1fe-6f3b-476b-a948-ecd43f290eef	organization	claim.name
1b16f1fe-6f3b-476b-a948-ecd43f290eef	String	jsonType.label
267e37b1-5575-45d7-96c3-d192d48aadd2	clientAddress	user.session.note
267e37b1-5575-45d7-96c3-d192d48aadd2	true	introspection.token.claim
267e37b1-5575-45d7-96c3-d192d48aadd2	true	id.token.claim
267e37b1-5575-45d7-96c3-d192d48aadd2	true	access.token.claim
267e37b1-5575-45d7-96c3-d192d48aadd2	clientAddress	claim.name
267e37b1-5575-45d7-96c3-d192d48aadd2	String	jsonType.label
8a9254ae-bb6e-43d5-a45f-155351a9f639	clientHost	user.session.note
8a9254ae-bb6e-43d5-a45f-155351a9f639	true	introspection.token.claim
8a9254ae-bb6e-43d5-a45f-155351a9f639	true	id.token.claim
8a9254ae-bb6e-43d5-a45f-155351a9f639	true	access.token.claim
8a9254ae-bb6e-43d5-a45f-155351a9f639	clientHost	claim.name
8a9254ae-bb6e-43d5-a45f-155351a9f639	String	jsonType.label
91aa6a86-666f-4103-a047-d0746f80c286	client_id	user.session.note
91aa6a86-666f-4103-a047-d0746f80c286	true	introspection.token.claim
91aa6a86-666f-4103-a047-d0746f80c286	true	id.token.claim
91aa6a86-666f-4103-a047-d0746f80c286	true	access.token.claim
91aa6a86-666f-4103-a047-d0746f80c286	client_id	claim.name
91aa6a86-666f-4103-a047-d0746f80c286	String	jsonType.label
0a099403-0cd3-401e-9743-8a45a301d8b1	false	single
0a099403-0cd3-401e-9743-8a45a301d8b1	Basic	attribute.nameformat
0a099403-0cd3-401e-9743-8a45a301d8b1	Role	attribute.name
0e2ece73-614f-4d70-8a95-f7000c3d7e32	true	introspection.token.claim
0e2ece73-614f-4d70-8a95-f7000c3d7e32	true	userinfo.token.claim
0e2ece73-614f-4d70-8a95-f7000c3d7e32	true	id.token.claim
0e2ece73-614f-4d70-8a95-f7000c3d7e32	true	access.token.claim
155edd6a-1da2-4339-84cb-536224c0a279	true	introspection.token.claim
155edd6a-1da2-4339-84cb-536224c0a279	true	userinfo.token.claim
155edd6a-1da2-4339-84cb-536224c0a279	picture	user.attribute
155edd6a-1da2-4339-84cb-536224c0a279	true	id.token.claim
155edd6a-1da2-4339-84cb-536224c0a279	true	access.token.claim
155edd6a-1da2-4339-84cb-536224c0a279	picture	claim.name
155edd6a-1da2-4339-84cb-536224c0a279	String	jsonType.label
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	true	introspection.token.claim
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	true	userinfo.token.claim
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	locale	user.attribute
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	true	id.token.claim
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	true	access.token.claim
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	locale	claim.name
2e0d64c9-a47a-4b47-9ab3-4fa3d8b99da3	String	jsonType.label
36298f66-7fdb-489a-8a9d-b917d934b756	true	introspection.token.claim
36298f66-7fdb-489a-8a9d-b917d934b756	true	userinfo.token.claim
36298f66-7fdb-489a-8a9d-b917d934b756	gender	user.attribute
36298f66-7fdb-489a-8a9d-b917d934b756	true	id.token.claim
36298f66-7fdb-489a-8a9d-b917d934b756	true	access.token.claim
36298f66-7fdb-489a-8a9d-b917d934b756	gender	claim.name
36298f66-7fdb-489a-8a9d-b917d934b756	String	jsonType.label
542672d3-2591-447d-b56a-4c4a0ea5d048	true	introspection.token.claim
542672d3-2591-447d-b56a-4c4a0ea5d048	true	userinfo.token.claim
542672d3-2591-447d-b56a-4c4a0ea5d048	username	user.attribute
542672d3-2591-447d-b56a-4c4a0ea5d048	true	id.token.claim
542672d3-2591-447d-b56a-4c4a0ea5d048	true	access.token.claim
542672d3-2591-447d-b56a-4c4a0ea5d048	preferred_username	claim.name
542672d3-2591-447d-b56a-4c4a0ea5d048	String	jsonType.label
5a34fd2f-c161-424d-83f6-569c37485b3b	true	introspection.token.claim
5a34fd2f-c161-424d-83f6-569c37485b3b	true	userinfo.token.claim
5a34fd2f-c161-424d-83f6-569c37485b3b	nickname	user.attribute
5a34fd2f-c161-424d-83f6-569c37485b3b	true	id.token.claim
5a34fd2f-c161-424d-83f6-569c37485b3b	true	access.token.claim
5a34fd2f-c161-424d-83f6-569c37485b3b	nickname	claim.name
5a34fd2f-c161-424d-83f6-569c37485b3b	String	jsonType.label
5c600047-533b-42c3-a582-0502c93d4734	true	introspection.token.claim
5c600047-533b-42c3-a582-0502c93d4734	true	userinfo.token.claim
5c600047-533b-42c3-a582-0502c93d4734	firstName	user.attribute
5c600047-533b-42c3-a582-0502c93d4734	true	id.token.claim
5c600047-533b-42c3-a582-0502c93d4734	true	access.token.claim
5c600047-533b-42c3-a582-0502c93d4734	given_name	claim.name
5c600047-533b-42c3-a582-0502c93d4734	String	jsonType.label
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	true	introspection.token.claim
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	true	userinfo.token.claim
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	website	user.attribute
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	true	id.token.claim
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	true	access.token.claim
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	website	claim.name
640c5796-d200-4d1f-a0c7-2faf0d5c0f3b	String	jsonType.label
66438034-4029-42fd-9cfa-2b58de7cfe7f	true	introspection.token.claim
66438034-4029-42fd-9cfa-2b58de7cfe7f	true	userinfo.token.claim
66438034-4029-42fd-9cfa-2b58de7cfe7f	birthdate	user.attribute
66438034-4029-42fd-9cfa-2b58de7cfe7f	true	id.token.claim
66438034-4029-42fd-9cfa-2b58de7cfe7f	true	access.token.claim
66438034-4029-42fd-9cfa-2b58de7cfe7f	birthdate	claim.name
66438034-4029-42fd-9cfa-2b58de7cfe7f	String	jsonType.label
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	true	introspection.token.claim
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	true	userinfo.token.claim
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	profile	user.attribute
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	true	id.token.claim
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	true	access.token.claim
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	profile	claim.name
6e75cb4d-741b-4a06-9ae4-b5ab22f91a1b	String	jsonType.label
7a0253cf-9389-4df4-8003-c83fb96bea96	true	introspection.token.claim
7a0253cf-9389-4df4-8003-c83fb96bea96	true	userinfo.token.claim
7a0253cf-9389-4df4-8003-c83fb96bea96	updatedAt	user.attribute
7a0253cf-9389-4df4-8003-c83fb96bea96	true	id.token.claim
7a0253cf-9389-4df4-8003-c83fb96bea96	true	access.token.claim
7a0253cf-9389-4df4-8003-c83fb96bea96	updated_at	claim.name
7a0253cf-9389-4df4-8003-c83fb96bea96	long	jsonType.label
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	true	introspection.token.claim
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	true	userinfo.token.claim
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	middleName	user.attribute
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	true	id.token.claim
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	true	access.token.claim
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	middle_name	claim.name
89c7e078-2908-45dc-bb70-d3eafd2aaa9b	String	jsonType.label
904b5766-f4af-4898-94b1-275dd2606a78	true	introspection.token.claim
904b5766-f4af-4898-94b1-275dd2606a78	true	userinfo.token.claim
904b5766-f4af-4898-94b1-275dd2606a78	lastName	user.attribute
904b5766-f4af-4898-94b1-275dd2606a78	true	id.token.claim
904b5766-f4af-4898-94b1-275dd2606a78	true	access.token.claim
904b5766-f4af-4898-94b1-275dd2606a78	family_name	claim.name
904b5766-f4af-4898-94b1-275dd2606a78	String	jsonType.label
c85b1178-4c69-46bb-b632-c45c5700f6d5	true	introspection.token.claim
c85b1178-4c69-46bb-b632-c45c5700f6d5	true	userinfo.token.claim
c85b1178-4c69-46bb-b632-c45c5700f6d5	zoneinfo	user.attribute
c85b1178-4c69-46bb-b632-c45c5700f6d5	true	id.token.claim
c85b1178-4c69-46bb-b632-c45c5700f6d5	true	access.token.claim
c85b1178-4c69-46bb-b632-c45c5700f6d5	zoneinfo	claim.name
c85b1178-4c69-46bb-b632-c45c5700f6d5	String	jsonType.label
7a3987a5-5a44-42d1-8bec-d5a6752526f2	true	introspection.token.claim
7a3987a5-5a44-42d1-8bec-d5a6752526f2	true	userinfo.token.claim
7a3987a5-5a44-42d1-8bec-d5a6752526f2	emailVerified	user.attribute
7a3987a5-5a44-42d1-8bec-d5a6752526f2	true	id.token.claim
7a3987a5-5a44-42d1-8bec-d5a6752526f2	true	access.token.claim
7a3987a5-5a44-42d1-8bec-d5a6752526f2	email_verified	claim.name
7a3987a5-5a44-42d1-8bec-d5a6752526f2	boolean	jsonType.label
88977265-3373-47ef-a417-f71b544a7a37	true	introspection.token.claim
88977265-3373-47ef-a417-f71b544a7a37	true	userinfo.token.claim
88977265-3373-47ef-a417-f71b544a7a37	email	user.attribute
88977265-3373-47ef-a417-f71b544a7a37	true	id.token.claim
88977265-3373-47ef-a417-f71b544a7a37	true	access.token.claim
88977265-3373-47ef-a417-f71b544a7a37	email	claim.name
88977265-3373-47ef-a417-f71b544a7a37	String	jsonType.label
8a78d2d3-fe3c-421d-9388-0e167d68eecf	formatted	user.attribute.formatted
8a78d2d3-fe3c-421d-9388-0e167d68eecf	country	user.attribute.country
8a78d2d3-fe3c-421d-9388-0e167d68eecf	true	introspection.token.claim
8a78d2d3-fe3c-421d-9388-0e167d68eecf	postal_code	user.attribute.postal_code
8a78d2d3-fe3c-421d-9388-0e167d68eecf	true	userinfo.token.claim
8a78d2d3-fe3c-421d-9388-0e167d68eecf	street	user.attribute.street
8a78d2d3-fe3c-421d-9388-0e167d68eecf	true	id.token.claim
8a78d2d3-fe3c-421d-9388-0e167d68eecf	region	user.attribute.region
8a78d2d3-fe3c-421d-9388-0e167d68eecf	true	access.token.claim
8a78d2d3-fe3c-421d-9388-0e167d68eecf	locality	user.attribute.locality
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	true	introspection.token.claim
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	true	userinfo.token.claim
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	phoneNumber	user.attribute
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	true	id.token.claim
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	true	access.token.claim
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	phone_number	claim.name
0bfd66c6-f193-4b3a-962b-3fd5d0c6c538	String	jsonType.label
94d23547-67d4-4530-9183-1ecb283eb941	true	introspection.token.claim
94d23547-67d4-4530-9183-1ecb283eb941	true	userinfo.token.claim
94d23547-67d4-4530-9183-1ecb283eb941	phoneNumberVerified	user.attribute
94d23547-67d4-4530-9183-1ecb283eb941	true	id.token.claim
94d23547-67d4-4530-9183-1ecb283eb941	true	access.token.claim
94d23547-67d4-4530-9183-1ecb283eb941	phone_number_verified	claim.name
94d23547-67d4-4530-9183-1ecb283eb941	boolean	jsonType.label
31794e9d-c8c4-4781-85c3-9b2e299f5c03	true	introspection.token.claim
31794e9d-c8c4-4781-85c3-9b2e299f5c03	true	multivalued
31794e9d-c8c4-4781-85c3-9b2e299f5c03	foo	user.attribute
31794e9d-c8c4-4781-85c3-9b2e299f5c03	true	access.token.claim
31794e9d-c8c4-4781-85c3-9b2e299f5c03	resource_access.${client_id}.roles	claim.name
31794e9d-c8c4-4781-85c3-9b2e299f5c03	String	jsonType.label
a3892f72-a43e-473a-acca-a6f703d5ecd9	true	introspection.token.claim
a3892f72-a43e-473a-acca-a6f703d5ecd9	true	multivalued
a3892f72-a43e-473a-acca-a6f703d5ecd9	foo	user.attribute
a3892f72-a43e-473a-acca-a6f703d5ecd9	true	access.token.claim
a3892f72-a43e-473a-acca-a6f703d5ecd9	realm_access.roles	claim.name
a3892f72-a43e-473a-acca-a6f703d5ecd9	String	jsonType.label
d884da86-671c-435b-b439-2415bd833069	true	introspection.token.claim
d884da86-671c-435b-b439-2415bd833069	true	access.token.claim
e84da861-f977-4459-8902-fd045d9af55d	true	introspection.token.claim
e84da861-f977-4459-8902-fd045d9af55d	true	access.token.claim
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	true	introspection.token.claim
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	true	userinfo.token.claim
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	username	user.attribute
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	true	id.token.claim
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	true	access.token.claim
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	upn	claim.name
d7609ca5-4d1f-47c9-a18b-f4b801c63e8b	String	jsonType.label
e3416dbb-0511-4715-aaa7-976684e46841	true	introspection.token.claim
e3416dbb-0511-4715-aaa7-976684e46841	true	multivalued
e3416dbb-0511-4715-aaa7-976684e46841	foo	user.attribute
e3416dbb-0511-4715-aaa7-976684e46841	true	id.token.claim
e3416dbb-0511-4715-aaa7-976684e46841	true	access.token.claim
e3416dbb-0511-4715-aaa7-976684e46841	groups	claim.name
e3416dbb-0511-4715-aaa7-976684e46841	String	jsonType.label
dc98e4bc-e696-456a-8f0c-45b3bd472cdb	true	introspection.token.claim
dc98e4bc-e696-456a-8f0c-45b3bd472cdb	true	id.token.claim
dc98e4bc-e696-456a-8f0c-45b3bd472cdb	true	access.token.claim
5f162570-5072-40ee-a473-ba2d07d65d30	AUTH_TIME	user.session.note
5f162570-5072-40ee-a473-ba2d07d65d30	true	introspection.token.claim
5f162570-5072-40ee-a473-ba2d07d65d30	true	id.token.claim
5f162570-5072-40ee-a473-ba2d07d65d30	true	access.token.claim
5f162570-5072-40ee-a473-ba2d07d65d30	auth_time	claim.name
5f162570-5072-40ee-a473-ba2d07d65d30	long	jsonType.label
633cab6a-b13a-44fc-89b6-33ab87385df3	true	introspection.token.claim
633cab6a-b13a-44fc-89b6-33ab87385df3	true	access.token.claim
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	true	introspection.token.claim
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	true	multivalued
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	true	id.token.claim
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	true	access.token.claim
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	organization	claim.name
bc12bcf3-af31-448a-acf9-f41cd2bbefcd	String	jsonType.label
cdd62daa-e865-4cac-bb8f-61e6133a8c63	true	introspection.token.claim
cdd62daa-e865-4cac-bb8f-61e6133a8c63	true	userinfo.token.claim
cdd62daa-e865-4cac-bb8f-61e6133a8c63	locale	user.attribute
cdd62daa-e865-4cac-bb8f-61e6133a8c63	true	id.token.claim
cdd62daa-e865-4cac-bb8f-61e6133a8c63	true	access.token.claim
cdd62daa-e865-4cac-bb8f-61e6133a8c63	locale	claim.name
cdd62daa-e865-4cac-bb8f-61e6133a8c63	String	jsonType.label
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	clientHost	user.session.note
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	true	introspection.token.claim
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	true	id.token.claim
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	true	access.token.claim
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	clientHost	claim.name
99ce2402-1901-40a1-8e7f-1ca0f30cdef9	String	jsonType.label
a0df39a9-d33c-4f47-88e8-72f913dfc222	client_id	user.session.note
a0df39a9-d33c-4f47-88e8-72f913dfc222	true	introspection.token.claim
a0df39a9-d33c-4f47-88e8-72f913dfc222	true	id.token.claim
a0df39a9-d33c-4f47-88e8-72f913dfc222	true	access.token.claim
a0df39a9-d33c-4f47-88e8-72f913dfc222	client_id	claim.name
a0df39a9-d33c-4f47-88e8-72f913dfc222	String	jsonType.label
ffd7a743-377f-4348-a94f-72f013cbc6ff	clientAddress	user.session.note
ffd7a743-377f-4348-a94f-72f013cbc6ff	true	introspection.token.claim
ffd7a743-377f-4348-a94f-72f013cbc6ff	true	id.token.claim
ffd7a743-377f-4348-a94f-72f013cbc6ff	true	access.token.claim
ffd7a743-377f-4348-a94f-72f013cbc6ff	clientAddress	claim.name
ffd7a743-377f-4348-a94f-72f013cbc6ff	String	jsonType.label
34f0436f-0d21-4a11-87ca-cf0d4107f4af	false	full.path
34f0436f-0d21-4a11-87ca-cf0d4107f4af	true	introspection.token.claim
34f0436f-0d21-4a11-87ca-cf0d4107f4af	true	userinfo.token.claim
34f0436f-0d21-4a11-87ca-cf0d4107f4af	true	id.token.claim
34f0436f-0d21-4a11-87ca-cf0d4107f4af	false	lightweight.claim
34f0436f-0d21-4a11-87ca-cf0d4107f4af	true	access.token.claim
34f0436f-0d21-4a11-87ca-cf0d4107f4af	tenant_id	claim.name
a8c7cb76-eb43-45ea-913f-a05b3b8a7707	false	single
a8c7cb76-eb43-45ea-913f-a05b3b8a7707	Basic	attribute.nameformat
a8c7cb76-eb43-45ea-913f-a05b3b8a7707	Role	attribute.name
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	true	introspection.token.claim
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	true	userinfo.token.claim
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	birthdate	user.attribute
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	true	id.token.claim
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	true	access.token.claim
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	birthdate	claim.name
2ee2a39d-a373-4ed3-be9c-e7eeaf89f8d0	String	jsonType.label
3636171f-ca8d-4cef-8f7a-96d4111898e1	true	introspection.token.claim
3636171f-ca8d-4cef-8f7a-96d4111898e1	true	userinfo.token.claim
3636171f-ca8d-4cef-8f7a-96d4111898e1	true	id.token.claim
3636171f-ca8d-4cef-8f7a-96d4111898e1	true	access.token.claim
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	true	introspection.token.claim
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	true	userinfo.token.claim
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	nickname	user.attribute
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	true	id.token.claim
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	true	access.token.claim
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	nickname	claim.name
3a1414fa-d5c0-4ceb-a274-50a9f2fbb37f	String	jsonType.label
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	true	introspection.token.claim
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	true	userinfo.token.claim
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	website	user.attribute
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	true	id.token.claim
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	true	access.token.claim
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	website	claim.name
44a96da5-bc16-49b9-9a17-a9e0e91c60bd	String	jsonType.label
46d1ca01-3aa9-462d-a617-074db6c38737	true	introspection.token.claim
46d1ca01-3aa9-462d-a617-074db6c38737	true	userinfo.token.claim
46d1ca01-3aa9-462d-a617-074db6c38737	firstName	user.attribute
46d1ca01-3aa9-462d-a617-074db6c38737	true	id.token.claim
46d1ca01-3aa9-462d-a617-074db6c38737	true	access.token.claim
46d1ca01-3aa9-462d-a617-074db6c38737	given_name	claim.name
46d1ca01-3aa9-462d-a617-074db6c38737	String	jsonType.label
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	true	introspection.token.claim
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	true	userinfo.token.claim
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	locale	user.attribute
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	true	id.token.claim
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	true	access.token.claim
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	locale	claim.name
4d82efe5-a21b-4f1e-9da8-9994a91b52dd	String	jsonType.label
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	true	introspection.token.claim
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	true	userinfo.token.claim
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	updatedAt	user.attribute
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	true	id.token.claim
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	true	access.token.claim
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	updated_at	claim.name
85d2465c-ddc0-4b19-8749-3fa18ac61f1a	long	jsonType.label
89019e02-d502-4286-90fe-b36eea42472e	true	introspection.token.claim
89019e02-d502-4286-90fe-b36eea42472e	true	userinfo.token.claim
89019e02-d502-4286-90fe-b36eea42472e	gender	user.attribute
89019e02-d502-4286-90fe-b36eea42472e	true	id.token.claim
89019e02-d502-4286-90fe-b36eea42472e	true	access.token.claim
89019e02-d502-4286-90fe-b36eea42472e	gender	claim.name
89019e02-d502-4286-90fe-b36eea42472e	String	jsonType.label
a32c9b61-04cf-4736-925f-b73f6bfffeb8	true	introspection.token.claim
a32c9b61-04cf-4736-925f-b73f6bfffeb8	true	userinfo.token.claim
a32c9b61-04cf-4736-925f-b73f6bfffeb8	picture	user.attribute
a32c9b61-04cf-4736-925f-b73f6bfffeb8	true	id.token.claim
a32c9b61-04cf-4736-925f-b73f6bfffeb8	true	access.token.claim
a32c9b61-04cf-4736-925f-b73f6bfffeb8	picture	claim.name
a32c9b61-04cf-4736-925f-b73f6bfffeb8	String	jsonType.label
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	true	introspection.token.claim
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	true	userinfo.token.claim
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	profile	user.attribute
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	true	id.token.claim
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	true	access.token.claim
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	profile	claim.name
af93acc9-e7e3-4ff4-84ef-c6fd4e4db6c0	String	jsonType.label
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	true	introspection.token.claim
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	true	userinfo.token.claim
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	zoneinfo	user.attribute
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	true	id.token.claim
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	true	access.token.claim
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	zoneinfo	claim.name
b83de0ce-0301-4f8b-86d0-6b9c44014bcb	String	jsonType.label
c54b61ac-5d09-41ea-9fde-d16a2807608c	true	introspection.token.claim
c54b61ac-5d09-41ea-9fde-d16a2807608c	true	userinfo.token.claim
c54b61ac-5d09-41ea-9fde-d16a2807608c	username	user.attribute
c54b61ac-5d09-41ea-9fde-d16a2807608c	true	id.token.claim
c54b61ac-5d09-41ea-9fde-d16a2807608c	true	access.token.claim
c54b61ac-5d09-41ea-9fde-d16a2807608c	preferred_username	claim.name
c54b61ac-5d09-41ea-9fde-d16a2807608c	String	jsonType.label
e00029b8-b027-4b72-b742-4237cf0c7a87	true	introspection.token.claim
e00029b8-b027-4b72-b742-4237cf0c7a87	true	userinfo.token.claim
e00029b8-b027-4b72-b742-4237cf0c7a87	lastName	user.attribute
e00029b8-b027-4b72-b742-4237cf0c7a87	true	id.token.claim
e00029b8-b027-4b72-b742-4237cf0c7a87	true	access.token.claim
e00029b8-b027-4b72-b742-4237cf0c7a87	family_name	claim.name
e00029b8-b027-4b72-b742-4237cf0c7a87	String	jsonType.label
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	true	introspection.token.claim
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	true	userinfo.token.claim
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	middleName	user.attribute
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	true	id.token.claim
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	true	access.token.claim
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	middle_name	claim.name
e52fedb2-b624-4c6c-958d-68ab2f6a0e0d	String	jsonType.label
8f21d49b-91c8-4909-b539-f5521bf6234b	true	introspection.token.claim
8f21d49b-91c8-4909-b539-f5521bf6234b	true	userinfo.token.claim
8f21d49b-91c8-4909-b539-f5521bf6234b	emailVerified	user.attribute
8f21d49b-91c8-4909-b539-f5521bf6234b	true	id.token.claim
8f21d49b-91c8-4909-b539-f5521bf6234b	true	access.token.claim
8f21d49b-91c8-4909-b539-f5521bf6234b	email_verified	claim.name
8f21d49b-91c8-4909-b539-f5521bf6234b	boolean	jsonType.label
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	true	introspection.token.claim
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	true	userinfo.token.claim
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	email	user.attribute
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	true	id.token.claim
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	true	access.token.claim
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	email	claim.name
ac4e8f0d-b724-474c-8577-6422f7b7ad8f	String	jsonType.label
46af36ba-8e32-477e-9c75-15c50ec25ff2	formatted	user.attribute.formatted
46af36ba-8e32-477e-9c75-15c50ec25ff2	country	user.attribute.country
46af36ba-8e32-477e-9c75-15c50ec25ff2	true	introspection.token.claim
46af36ba-8e32-477e-9c75-15c50ec25ff2	postal_code	user.attribute.postal_code
46af36ba-8e32-477e-9c75-15c50ec25ff2	true	userinfo.token.claim
46af36ba-8e32-477e-9c75-15c50ec25ff2	street	user.attribute.street
46af36ba-8e32-477e-9c75-15c50ec25ff2	true	id.token.claim
46af36ba-8e32-477e-9c75-15c50ec25ff2	region	user.attribute.region
46af36ba-8e32-477e-9c75-15c50ec25ff2	true	access.token.claim
46af36ba-8e32-477e-9c75-15c50ec25ff2	locality	user.attribute.locality
743e4d02-43d1-446c-ba80-e97a57962970	true	introspection.token.claim
743e4d02-43d1-446c-ba80-e97a57962970	true	userinfo.token.claim
743e4d02-43d1-446c-ba80-e97a57962970	phoneNumber	user.attribute
743e4d02-43d1-446c-ba80-e97a57962970	true	id.token.claim
743e4d02-43d1-446c-ba80-e97a57962970	true	access.token.claim
743e4d02-43d1-446c-ba80-e97a57962970	phone_number	claim.name
743e4d02-43d1-446c-ba80-e97a57962970	String	jsonType.label
fd9ffc25-143c-4569-b59b-61b7d47461b6	true	introspection.token.claim
fd9ffc25-143c-4569-b59b-61b7d47461b6	true	userinfo.token.claim
fd9ffc25-143c-4569-b59b-61b7d47461b6	phoneNumberVerified	user.attribute
fd9ffc25-143c-4569-b59b-61b7d47461b6	true	id.token.claim
fd9ffc25-143c-4569-b59b-61b7d47461b6	true	access.token.claim
fd9ffc25-143c-4569-b59b-61b7d47461b6	phone_number_verified	claim.name
fd9ffc25-143c-4569-b59b-61b7d47461b6	boolean	jsonType.label
4482779f-c2aa-4b40-9d72-ed0a271dbd00	true	introspection.token.claim
4482779f-c2aa-4b40-9d72-ed0a271dbd00	true	multivalued
4482779f-c2aa-4b40-9d72-ed0a271dbd00	foo	user.attribute
4482779f-c2aa-4b40-9d72-ed0a271dbd00	true	access.token.claim
4482779f-c2aa-4b40-9d72-ed0a271dbd00	realm_access.roles	claim.name
4482779f-c2aa-4b40-9d72-ed0a271dbd00	String	jsonType.label
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	true	introspection.token.claim
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	true	multivalued
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	foo	user.attribute
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	true	access.token.claim
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	resource_access.${client_id}.roles	claim.name
ce4cb4fb-26e5-4dc5-b9bd-210d043b344c	String	jsonType.label
dfda790c-e7d7-48c6-a806-39595bbdcd95	true	introspection.token.claim
dfda790c-e7d7-48c6-a806-39595bbdcd95	true	access.token.claim
381f34b1-b471-44e1-ac96-4453a9b22034	true	introspection.token.claim
381f34b1-b471-44e1-ac96-4453a9b22034	true	access.token.claim
74a56ac8-acbc-4307-9555-4def3d2d04ca	true	introspection.token.claim
74a56ac8-acbc-4307-9555-4def3d2d04ca	true	userinfo.token.claim
74a56ac8-acbc-4307-9555-4def3d2d04ca	username	user.attribute
74a56ac8-acbc-4307-9555-4def3d2d04ca	true	id.token.claim
74a56ac8-acbc-4307-9555-4def3d2d04ca	true	access.token.claim
74a56ac8-acbc-4307-9555-4def3d2d04ca	upn	claim.name
74a56ac8-acbc-4307-9555-4def3d2d04ca	String	jsonType.label
9bfad321-7544-4a01-a3fd-3278dacb4b0a	true	introspection.token.claim
9bfad321-7544-4a01-a3fd-3278dacb4b0a	true	multivalued
9bfad321-7544-4a01-a3fd-3278dacb4b0a	foo	user.attribute
9bfad321-7544-4a01-a3fd-3278dacb4b0a	true	id.token.claim
9bfad321-7544-4a01-a3fd-3278dacb4b0a	true	access.token.claim
9bfad321-7544-4a01-a3fd-3278dacb4b0a	groups	claim.name
9bfad321-7544-4a01-a3fd-3278dacb4b0a	String	jsonType.label
335b2043-1f16-44d0-a0ae-64e95c8ca0ea	true	introspection.token.claim
335b2043-1f16-44d0-a0ae-64e95c8ca0ea	true	id.token.claim
335b2043-1f16-44d0-a0ae-64e95c8ca0ea	true	access.token.claim
39f38d03-5e6e-468e-8ad1-3b2ee314639d	AUTH_TIME	user.session.note
39f38d03-5e6e-468e-8ad1-3b2ee314639d	true	introspection.token.claim
39f38d03-5e6e-468e-8ad1-3b2ee314639d	true	id.token.claim
39f38d03-5e6e-468e-8ad1-3b2ee314639d	true	access.token.claim
39f38d03-5e6e-468e-8ad1-3b2ee314639d	auth_time	claim.name
39f38d03-5e6e-468e-8ad1-3b2ee314639d	long	jsonType.label
e243b7dd-8eae-40ef-844f-0c82f610d33a	true	introspection.token.claim
e243b7dd-8eae-40ef-844f-0c82f610d33a	true	access.token.claim
333d0313-8a63-4887-b996-fbd4dd6e5c89	true	introspection.token.claim
333d0313-8a63-4887-b996-fbd4dd6e5c89	true	multivalued
333d0313-8a63-4887-b996-fbd4dd6e5c89	true	id.token.claim
333d0313-8a63-4887-b996-fbd4dd6e5c89	true	access.token.claim
333d0313-8a63-4887-b996-fbd4dd6e5c89	organization	claim.name
333d0313-8a63-4887-b996-fbd4dd6e5c89	String	jsonType.label
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	true	introspection.token.claim
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	true	userinfo.token.claim
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	locale	user.attribute
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	true	id.token.claim
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	true	access.token.claim
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	locale	claim.name
021cb9fb-e66d-4d7c-a6aa-054a9bceb770	String	jsonType.label
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	clientHost	user.session.note
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	true	introspection.token.claim
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	true	id.token.claim
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	true	access.token.claim
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	clientHost	claim.name
1673e9ea-3fea-4883-b9a6-f1f3210f5be7	String	jsonType.label
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	clientAddress	user.session.note
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	true	introspection.token.claim
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	true	id.token.claim
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	true	access.token.claim
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	clientAddress	claim.name
2fa62d2e-c6a2-4bfb-b639-63b9399e2183	String	jsonType.label
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	client_id	user.session.note
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	true	introspection.token.claim
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	true	id.token.claim
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	true	access.token.claim
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	client_id	claim.name
8a3bc9ca-42a3-4077-b552-ecdaedab1c64	String	jsonType.label
78937947-ad50-42f2-963d-b4de649ebfd6	false	aggregate.attrs
78937947-ad50-42f2-963d-b4de649ebfd6	true	userinfo.token.claim
78937947-ad50-42f2-963d-b4de649ebfd6	false	multivalued
78937947-ad50-42f2-963d-b4de649ebfd6	tenant_id	user.attribute
78937947-ad50-42f2-963d-b4de649ebfd6	true	id.token.claim
78937947-ad50-42f2-963d-b4de649ebfd6	true	access.token.claim
78937947-ad50-42f2-963d-b4de649ebfd6	tenant_id	claim.name
78937947-ad50-42f2-963d-b4de649ebfd6	String	jsonType.label
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	false	aggregate.attrs
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	true	introspection.token.claim
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	false	multivalued
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	true	userinfo.token.claim
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	must_change_password	user.attribute
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	true	id.token.claim
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	true	access.token.claim
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	must_change_password	claim.name
2ce55ef8-56eb-48a0-abd2-170d2c8fb41e	String	jsonType.label
\.


--
-- Data for Name: realm; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm (id, access_code_lifespan, user_action_lifespan, access_token_lifespan, account_theme, admin_theme, email_theme, enabled, events_enabled, events_expiration, login_theme, name, not_before, password_policy, registration_allowed, remember_me, reset_password_allowed, social, ssl_required, sso_idle_timeout, sso_max_lifespan, update_profile_on_soc_login, verify_email, master_admin_client, login_lifespan, internationalization_enabled, default_locale, reg_email_as_username, admin_events_enabled, admin_events_details_enabled, edit_username_allowed, otp_policy_counter, otp_policy_window, otp_policy_period, otp_policy_digits, otp_policy_alg, otp_policy_type, browser_flow, registration_flow, direct_grant_flow, reset_credentials_flow, client_auth_flow, offline_session_idle_timeout, revoke_refresh_token, access_token_life_implicit, login_with_email_allowed, duplicate_emails_allowed, docker_auth_flow, refresh_token_max_reuse, allow_user_managed_access, sso_max_lifespan_remember_me, sso_idle_timeout_remember_me, default_role) FROM stdin;
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	60	300	300	\N	\N	\N	t	f	0	\N	platforme_school	0	\N	f	f	f	f	EXTERNAL	1800	36000	f	f	741ac3f4-ce6b-4436-ba7a-92d7ce37c504	1800	f	\N	f	f	f	f	0	1	30	6	HmacSHA1	totp	f28f4ca9-ac34-4863-8736-baa41db4d706	7ef380b4-c2ce-4684-ac9c-66fcedec973c	c385709a-f0bc-405e-8b86-6502e1bd1709	7880a0dc-0269-4832-bf7e-12e3dc766ffc	fad0f712-db7c-45c6-bab3-266fb23cdfd7	2592000	f	900	t	f	cbc309e1-4dad-4566-939d-3a5965196003	0	f	0	0	3209610d-c3e0-478f-9e69-5078e51fa2e2
259bfe83-5374-4ae7-bb38-5464d0d5bc75	60	300	60	\N	\N	\N	t	f	0	\N	master	0	\N	f	f	f	f	EXTERNAL	1800	36000	f	f	21023f50-efbf-4680-94a0-dd36232f70b2	1800	f	\N	f	f	f	f	0	1	30	6	HmacSHA1	totp	8d932c23-9b14-4631-9115-68fb5c1280e6	3d047957-c675-4bd6-b8b8-ba1388bfe7bc	8ea741aa-a19b-4f69-85f2-c44caced0001	e21e4d5f-321d-48a6-96b6-91e5c4b140b3	2a7e05b5-60dc-4103-92af-1cbca75f5a5f	2592000	f	900	t	f	747a49b9-eea1-4846-b2f5-3a7543b6cb5b	0	f	0	0	1abec7ba-4d9e-4b1c-96b6-89bd6a10416f
83e82497-9a85-4600-9b3d-877c38c4ff59	60	300	300	\N	\N	\N	t	f	0	\N	smartschool	0	\N	f	f	t	f	EXTERNAL	1800	36000	f	f	9c0c17dd-d663-42ff-a095-bfdfe00c32c0	1800	f	\N	f	f	f	f	0	1	30	6	HmacSHA1	totp	de9d9e28-6996-4778-ab16-16090e34971b	828a6cd0-d3ce-4aac-9db0-b3f4f8503425	74ea7d61-4e60-4d7c-bbe2-2d7d141f2c0f	9b5cc723-5e89-4420-a861-9215405ca68e	29c3a662-3824-4f5a-a9f0-5af5be80698f	2592000	f	900	t	f	f4ce9663-c40a-4556-8483-bda50deee9ba	0	f	0	0	da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e
\.


--
-- Data for Name: realm_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_attribute (name, realm_id, value) FROM stdin;
_browser_header.contentSecurityPolicyReportOnly	259bfe83-5374-4ae7-bb38-5464d0d5bc75	
_browser_header.xContentTypeOptions	259bfe83-5374-4ae7-bb38-5464d0d5bc75	nosniff
_browser_header.referrerPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	no-referrer
_browser_header.xRobotsTag	259bfe83-5374-4ae7-bb38-5464d0d5bc75	none
_browser_header.xFrameOptions	259bfe83-5374-4ae7-bb38-5464d0d5bc75	SAMEORIGIN
_browser_header.contentSecurityPolicy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	frame-src 'self'; frame-ancestors 'self'; object-src 'none';
_browser_header.xXSSProtection	259bfe83-5374-4ae7-bb38-5464d0d5bc75	1; mode=block
_browser_header.strictTransportSecurity	259bfe83-5374-4ae7-bb38-5464d0d5bc75	max-age=31536000; includeSubDomains
bruteForceProtected	259bfe83-5374-4ae7-bb38-5464d0d5bc75	false
permanentLockout	259bfe83-5374-4ae7-bb38-5464d0d5bc75	false
maxTemporaryLockouts	259bfe83-5374-4ae7-bb38-5464d0d5bc75	0
bruteForceStrategy	259bfe83-5374-4ae7-bb38-5464d0d5bc75	MULTIPLE
maxFailureWaitSeconds	259bfe83-5374-4ae7-bb38-5464d0d5bc75	900
minimumQuickLoginWaitSeconds	259bfe83-5374-4ae7-bb38-5464d0d5bc75	60
waitIncrementSeconds	259bfe83-5374-4ae7-bb38-5464d0d5bc75	60
quickLoginCheckMilliSeconds	259bfe83-5374-4ae7-bb38-5464d0d5bc75	1000
maxDeltaTimeSeconds	259bfe83-5374-4ae7-bb38-5464d0d5bc75	43200
failureFactor	259bfe83-5374-4ae7-bb38-5464d0d5bc75	30
realmReusableOtpCode	259bfe83-5374-4ae7-bb38-5464d0d5bc75	false
firstBrokerLoginFlowId	259bfe83-5374-4ae7-bb38-5464d0d5bc75	e89f2d94-88af-4e46-9ebd-42ff5d0bed91
displayName	259bfe83-5374-4ae7-bb38-5464d0d5bc75	Keycloak
displayNameHtml	259bfe83-5374-4ae7-bb38-5464d0d5bc75	<div class="kc-logo-text"><span>Keycloak</span></div>
defaultSignatureAlgorithm	259bfe83-5374-4ae7-bb38-5464d0d5bc75	RS256
offlineSessionMaxLifespanEnabled	259bfe83-5374-4ae7-bb38-5464d0d5bc75	false
offlineSessionMaxLifespan	259bfe83-5374-4ae7-bb38-5464d0d5bc75	5184000
_browser_header.contentSecurityPolicyReportOnly	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	
_browser_header.xContentTypeOptions	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	nosniff
_browser_header.referrerPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	no-referrer
_browser_header.xRobotsTag	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	none
_browser_header.xFrameOptions	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	SAMEORIGIN
_browser_header.contentSecurityPolicy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	frame-src 'self'; frame-ancestors 'self'; object-src 'none';
_browser_header.xXSSProtection	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	1; mode=block
_browser_header.strictTransportSecurity	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	max-age=31536000; includeSubDomains
bruteForceProtected	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
permanentLockout	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
maxTemporaryLockouts	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0
bruteForceStrategy	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	MULTIPLE
maxFailureWaitSeconds	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	900
minimumQuickLoginWaitSeconds	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	60
waitIncrementSeconds	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	60
quickLoginCheckMilliSeconds	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	1000
maxDeltaTimeSeconds	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	43200
failureFactor	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	30
realmReusableOtpCode	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
defaultSignatureAlgorithm	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	RS256
offlineSessionMaxLifespanEnabled	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
offlineSessionMaxLifespan	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	5184000
actionTokenGeneratedByAdminLifespan	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	43200
actionTokenGeneratedByUserLifespan	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	300
oauth2DeviceCodeLifespan	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	600
oauth2DevicePollingInterval	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	5
webAuthnPolicyRpEntityName	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	keycloak
webAuthnPolicySignatureAlgorithms	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	ES256,RS256
webAuthnPolicyRpId	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	
webAuthnPolicyAttestationConveyancePreference	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyAuthenticatorAttachment	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyRequireResidentKey	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyUserVerificationRequirement	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyCreateTimeout	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0
webAuthnPolicyAvoidSameAuthenticatorRegister	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
webAuthnPolicyRpEntityNamePasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	keycloak
webAuthnPolicySignatureAlgorithmsPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	ES256,RS256
webAuthnPolicyRpIdPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	
webAuthnPolicyAttestationConveyancePreferencePasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyAuthenticatorAttachmentPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyRequireResidentKeyPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyUserVerificationRequirementPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	not specified
webAuthnPolicyCreateTimeoutPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0
webAuthnPolicyAvoidSameAuthenticatorRegisterPasswordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	false
cibaBackchannelTokenDeliveryMode	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	poll
cibaExpiresIn	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	120
cibaInterval	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	5
cibaAuthRequestedUserHint	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	login_hint
parRequestUriLifespan	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	60
firstBrokerLoginFlowId	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	0fa4a3c3-4a74-4550-a46a-e518cadc1b64
_browser_header.contentSecurityPolicyReportOnly	83e82497-9a85-4600-9b3d-877c38c4ff59	
_browser_header.xContentTypeOptions	83e82497-9a85-4600-9b3d-877c38c4ff59	nosniff
_browser_header.referrerPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	no-referrer
_browser_header.xRobotsTag	83e82497-9a85-4600-9b3d-877c38c4ff59	none
_browser_header.xFrameOptions	83e82497-9a85-4600-9b3d-877c38c4ff59	SAMEORIGIN
_browser_header.contentSecurityPolicy	83e82497-9a85-4600-9b3d-877c38c4ff59	frame-src 'self'; frame-ancestors 'self'; object-src 'none';
_browser_header.xXSSProtection	83e82497-9a85-4600-9b3d-877c38c4ff59	1; mode=block
_browser_header.strictTransportSecurity	83e82497-9a85-4600-9b3d-877c38c4ff59	max-age=31536000; includeSubDomains
permanentLockout	83e82497-9a85-4600-9b3d-877c38c4ff59	false
maxTemporaryLockouts	83e82497-9a85-4600-9b3d-877c38c4ff59	0
bruteForceStrategy	83e82497-9a85-4600-9b3d-877c38c4ff59	MULTIPLE
maxFailureWaitSeconds	83e82497-9a85-4600-9b3d-877c38c4ff59	900
minimumQuickLoginWaitSeconds	83e82497-9a85-4600-9b3d-877c38c4ff59	60
waitIncrementSeconds	83e82497-9a85-4600-9b3d-877c38c4ff59	60
quickLoginCheckMilliSeconds	83e82497-9a85-4600-9b3d-877c38c4ff59	1000
maxDeltaTimeSeconds	83e82497-9a85-4600-9b3d-877c38c4ff59	43200
failureFactor	83e82497-9a85-4600-9b3d-877c38c4ff59	30
realmReusableOtpCode	83e82497-9a85-4600-9b3d-877c38c4ff59	false
displayName	83e82497-9a85-4600-9b3d-877c38c4ff59	SmartSchool
defaultSignatureAlgorithm	83e82497-9a85-4600-9b3d-877c38c4ff59	RS256
bruteForceProtected	83e82497-9a85-4600-9b3d-877c38c4ff59	true
offlineSessionMaxLifespanEnabled	83e82497-9a85-4600-9b3d-877c38c4ff59	false
offlineSessionMaxLifespan	83e82497-9a85-4600-9b3d-877c38c4ff59	5184000
actionTokenGeneratedByAdminLifespan	83e82497-9a85-4600-9b3d-877c38c4ff59	43200
actionTokenGeneratedByUserLifespan	83e82497-9a85-4600-9b3d-877c38c4ff59	300
oauth2DeviceCodeLifespan	83e82497-9a85-4600-9b3d-877c38c4ff59	600
oauth2DevicePollingInterval	83e82497-9a85-4600-9b3d-877c38c4ff59	5
webAuthnPolicyRpEntityName	83e82497-9a85-4600-9b3d-877c38c4ff59	keycloak
webAuthnPolicySignatureAlgorithms	83e82497-9a85-4600-9b3d-877c38c4ff59	ES256,RS256
webAuthnPolicyRpId	83e82497-9a85-4600-9b3d-877c38c4ff59	
webAuthnPolicyAttestationConveyancePreference	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyAuthenticatorAttachment	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyRequireResidentKey	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyUserVerificationRequirement	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyCreateTimeout	83e82497-9a85-4600-9b3d-877c38c4ff59	0
webAuthnPolicyAvoidSameAuthenticatorRegister	83e82497-9a85-4600-9b3d-877c38c4ff59	false
webAuthnPolicyRpEntityNamePasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	keycloak
webAuthnPolicySignatureAlgorithmsPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	ES256,RS256
webAuthnPolicyRpIdPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	
webAuthnPolicyAttestationConveyancePreferencePasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyAuthenticatorAttachmentPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyRequireResidentKeyPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyUserVerificationRequirementPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	not specified
webAuthnPolicyCreateTimeoutPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	0
webAuthnPolicyAvoidSameAuthenticatorRegisterPasswordless	83e82497-9a85-4600-9b3d-877c38c4ff59	false
cibaBackchannelTokenDeliveryMode	83e82497-9a85-4600-9b3d-877c38c4ff59	poll
cibaExpiresIn	83e82497-9a85-4600-9b3d-877c38c4ff59	120
cibaInterval	83e82497-9a85-4600-9b3d-877c38c4ff59	5
cibaAuthRequestedUserHint	83e82497-9a85-4600-9b3d-877c38c4ff59	login_hint
parRequestUriLifespan	83e82497-9a85-4600-9b3d-877c38c4ff59	60
firstBrokerLoginFlowId	83e82497-9a85-4600-9b3d-877c38c4ff59	68469895-a771-4df2-881c-ccad0a7e8081
\.


--
-- Data for Name: realm_default_groups; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_default_groups (realm_id, group_id) FROM stdin;
\.


--
-- Data for Name: realm_enabled_event_types; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_enabled_event_types (realm_id, value) FROM stdin;
\.


--
-- Data for Name: realm_events_listeners; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_events_listeners (realm_id, value) FROM stdin;
259bfe83-5374-4ae7-bb38-5464d0d5bc75	jboss-logging
0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	jboss-logging
83e82497-9a85-4600-9b3d-877c38c4ff59	jboss-logging
\.


--
-- Data for Name: realm_localizations; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_localizations (realm_id, locale, texts) FROM stdin;
\.


--
-- Data for Name: realm_required_credential; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_required_credential (type, form_label, input, secret, realm_id) FROM stdin;
password	password	t	t	259bfe83-5374-4ae7-bb38-5464d0d5bc75
password	password	t	t	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26
password	password	t	t	83e82497-9a85-4600-9b3d-877c38c4ff59
\.


--
-- Data for Name: realm_smtp_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_smtp_config (realm_id, value, name) FROM stdin;
\.


--
-- Data for Name: realm_supported_locales; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.realm_supported_locales (realm_id, value) FROM stdin;
\.


--
-- Data for Name: redirect_uris; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.redirect_uris (client_id, value) FROM stdin;
3d56e973-75fb-4d57-bb78-302af297fe9d	/realms/master/account/*
1468f635-7aee-4d08-9633-86593942e889	/realms/master/account/*
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	/admin/master/console/*
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	/*
d022332e-3f8b-44dc-9e5c-c6751920ab3d	/realms/platforme_school/account/*
d2dfba85-0770-401f-9388-86bb979ab264	/realms/platforme_school/account/*
cbcbcbde-a715-4358-a3c3-7f027c4436b6	/admin/platforme_school/console/*
2d441a2b-1316-4adc-b51a-8e2520fc33e6	/*
81b24093-5b2e-4796-9adc-970cd6e97d98	http://localhost:3000/*
4a7c7844-cde9-4654-b214-b2bd003f2344	/realms/smartschool/account/*
cba0a035-784d-4ec0-9c98-64f8bb89002b	/realms/smartschool/account/*
d5442a0f-3191-407b-befa-391aaa28773d	/admin/smartschool/console/*
2e5dede9-f6b2-489c-8c2b-44ca02f30878	http://localhost:3000/*
2e5dede9-f6b2-489c-8c2b-44ca02f30878	http://localhost:3001/*
\.


--
-- Data for Name: required_action_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.required_action_config (required_action_id, value, name) FROM stdin;
\.


--
-- Data for Name: required_action_provider; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.required_action_provider (id, alias, name, realm_id, enabled, default_action, provider_id, priority) FROM stdin;
f5498fc0-bf24-4fd5-b90f-bf8c50920701	VERIFY_EMAIL	Verify Email	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	VERIFY_EMAIL	50
027db8cd-46c7-4096-8813-59a1255d1a16	UPDATE_PROFILE	Update Profile	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	UPDATE_PROFILE	40
efce3b89-382e-42da-8827-054c70505b73	CONFIGURE_TOTP	Configure OTP	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	CONFIGURE_TOTP	10
82d37426-f75a-406d-be1d-89a244729403	UPDATE_PASSWORD	Update Password	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	UPDATE_PASSWORD	30
96567212-d42c-48a2-b684-778fb01b9a31	TERMS_AND_CONDITIONS	Terms and Conditions	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	f	TERMS_AND_CONDITIONS	20
752d2833-6f0e-40f6-9989-d1dcc4d41654	delete_account	Delete Account	259bfe83-5374-4ae7-bb38-5464d0d5bc75	f	f	delete_account	60
b829f9ef-afb0-499b-b4b8-b1dde710e53f	delete_credential	Delete Credential	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	delete_credential	100
a5490af6-95d6-4bff-8801-132b3d382ca9	update_user_locale	Update User Locale	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	update_user_locale	1000
e57098d7-8f8d-465c-93e2-2e285ddb9e08	webauthn-register	Webauthn Register	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	webauthn-register	70
d3557475-258d-40c4-b406-bf88b4bab9dd	webauthn-register-passwordless	Webauthn Register Passwordless	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	webauthn-register-passwordless	80
c665efe9-e0b2-49af-8e97-8be9905f1c19	VERIFY_PROFILE	Verify Profile	259bfe83-5374-4ae7-bb38-5464d0d5bc75	t	f	VERIFY_PROFILE	90
f609d809-4b2c-45d1-8c4e-13776105a5e8	VERIFY_EMAIL	Verify Email	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	VERIFY_EMAIL	50
d9059a5d-d007-46b2-86de-743a7dc35fba	UPDATE_PROFILE	Update Profile	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	UPDATE_PROFILE	40
cf3ef7b7-e4d3-425f-9ed3-aca6dab05295	CONFIGURE_TOTP	Configure OTP	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	CONFIGURE_TOTP	10
0f1f693c-af91-4dee-9437-2d0e12737450	UPDATE_PASSWORD	Update Password	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	UPDATE_PASSWORD	30
1fc71d0b-006d-4ebc-832a-7c8df39d9a54	TERMS_AND_CONDITIONS	Terms and Conditions	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	f	TERMS_AND_CONDITIONS	20
f141584b-de7c-4406-9ab2-78f018f92ed4	delete_account	Delete Account	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	f	f	delete_account	60
2cbe0730-bbb7-431b-9173-93859a16ef97	delete_credential	Delete Credential	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	delete_credential	100
0d4910fc-aef0-4475-bf71-cb04ec5ab0d2	update_user_locale	Update User Locale	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	update_user_locale	1000
8ad0f418-ed45-4dae-99f2-9561a7f5e18b	webauthn-register	Webauthn Register	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	webauthn-register	70
0da8255d-c079-4c41-96e9-25cb81f86a2e	webauthn-register-passwordless	Webauthn Register Passwordless	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	webauthn-register-passwordless	80
26fd92ca-2680-42a7-b459-d1dcee5b544f	VERIFY_PROFILE	Verify Profile	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	t	f	VERIFY_PROFILE	90
8c548eff-291a-4dcc-8f45-245f03cc92d4	VERIFY_EMAIL	Verify Email	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	VERIFY_EMAIL	50
5bb307c9-420e-417a-a8ac-cc5228f39730	UPDATE_PROFILE	Update Profile	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	UPDATE_PROFILE	40
22659573-3fb4-4119-98b5-6047b8fe212a	CONFIGURE_TOTP	Configure OTP	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	CONFIGURE_TOTP	10
b7fb9bea-13ae-4676-8861-d8956650f1d3	UPDATE_PASSWORD	Update Password	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	UPDATE_PASSWORD	30
542dc4ce-7e68-4620-8a11-4f39422d93fa	TERMS_AND_CONDITIONS	Terms and Conditions	83e82497-9a85-4600-9b3d-877c38c4ff59	f	f	TERMS_AND_CONDITIONS	20
22ae3cae-716a-4bd8-b8df-be35384f748d	delete_account	Delete Account	83e82497-9a85-4600-9b3d-877c38c4ff59	f	f	delete_account	60
8f77c2cf-7477-4973-81c4-c8025a4ba59d	delete_credential	Delete Credential	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	delete_credential	100
9658ea6a-9467-4f36-9791-5ca68cc625b7	update_user_locale	Update User Locale	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	update_user_locale	1000
48450332-74ee-42d1-8506-12c89e0d1b3e	webauthn-register	Webauthn Register	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	webauthn-register	70
8be8e67f-97ef-4e11-b624-2226ff133339	webauthn-register-passwordless	Webauthn Register Passwordless	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	webauthn-register-passwordless	80
95c4e2d5-9058-4895-9b97-fed346cb8075	VERIFY_PROFILE	Verify Profile	83e82497-9a85-4600-9b3d-877c38c4ff59	t	f	VERIFY_PROFILE	90
\.


--
-- Data for Name: resource_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_attribute (id, name, value, resource_id) FROM stdin;
\.


--
-- Data for Name: resource_policy; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_policy (resource_id, policy_id) FROM stdin;
\.


--
-- Data for Name: resource_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_scope (resource_id, scope_id) FROM stdin;
\.


--
-- Data for Name: resource_server; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_server (id, allow_rs_remote_mgmt, policy_enforce_mode, decision_strategy) FROM stdin;
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	t	0	1
\.


--
-- Data for Name: resource_server_perm_ticket; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_server_perm_ticket (id, owner, requester, created_timestamp, granted_timestamp, resource_id, scope_id, resource_server_id, policy_id) FROM stdin;
\.


--
-- Data for Name: resource_server_policy; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_server_policy (id, name, description, type, decision_strategy, logic, resource_server_id, owner) FROM stdin;
da878d11-f3ea-45ca-9891-e32fb38cfbd0	Default Policy	A policy that grants access only for users within this realm	js	0	0	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
5bb8041c-a9d7-4550-bb01-82fa40c4ac7a	Default Permission	A permission that applies to the default resource type	resource	1	0	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	\N
\.


--
-- Data for Name: resource_server_resource; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_server_resource (id, name, type, icon_uri, owner, resource_server_id, owner_managed_access, display_name) FROM stdin;
d8755c1e-73df-4d80-8874-ba4701cf1c66	Default Resource	urn:backend:resources:default	\N	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	f	\N
\.


--
-- Data for Name: resource_server_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_server_scope (id, name, icon_uri, resource_server_id, display_name) FROM stdin;
\.


--
-- Data for Name: resource_uris; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.resource_uris (resource_id, value) FROM stdin;
d8755c1e-73df-4d80-8874-ba4701cf1c66	/*
\.


--
-- Data for Name: revoked_token; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.revoked_token (id, expire) FROM stdin;
\.


--
-- Data for Name: role_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.role_attribute (id, role_id, name, value) FROM stdin;
\.


--
-- Data for Name: scope_mapping; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.scope_mapping (client_id, role_id) FROM stdin;
1468f635-7aee-4d08-9633-86593942e889	342d9096-b03f-4326-b1b4-b137b8d2e348
1468f635-7aee-4d08-9633-86593942e889	74c27623-d249-453b-8038-6505f49139ba
d2dfba85-0770-401f-9388-86bb979ab264	ddc79483-a4aa-4928-b496-02a84d6ada27
d2dfba85-0770-401f-9388-86bb979ab264	b3b02a95-8f7d-43ca-9be9-56a5c07f65d8
cba0a035-784d-4ec0-9c98-64f8bb89002b	5f0fb361-0dea-4069-9142-70491c0ed68f
cba0a035-784d-4ec0-9c98-64f8bb89002b	743c5ae7-d2a2-4350-9c14-d4a66aad2752
\.


--
-- Data for Name: scope_policy; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.scope_policy (scope_id, policy_id) FROM stdin;
\.


--
-- Data for Name: user_attribute; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_attribute (name, value, user_id, id, long_value_hash, long_value_hash_lower_case, long_value) FROM stdin;
is_temporary_admin	true	a411426c-8d55-462b-9a76-04c5ccabd118	f13db0df-a8af-478c-8fa3-ca1b4a0bf976	\N	\N	\N
tenant_id	system	b0afe140-0b06-4065-9e40-bffe43e90b88	40bb31a1-0171-4316-bccd-7830cec8a6a4	\N	\N	\N
tenant_id	1	0f20480f-07ae-41b1-9c2a-eab9d4343ec6	73be613a-4d1f-4c61-aa71-a3381ab3d244	\N	\N	\N
tenant_id	2	b03867b6-332e-4a47-99cf-41f77263429e	3b62e67c-3f58-418d-9ab3-07e6f0e2ad7e	\N	\N	\N
tenant_id	3	c597c98c-3ca9-4af7-a1cf-28a178a5d9f5	bafed97f-f425-4631-8d30-b9a67a7676c9	\N	\N	\N
tenant_id	10	a27397a0-9714-4251-a9cb-fc6cdadd1c14	4c846bbc-a475-4d10-9fee-575a39a28b38	\N	\N	\N
tenant_id	11	ec07d9dc-1d34-459a-afce-b20256b34bae	3db372f2-a7d4-4879-aff3-f43f1ae5addd	\N	\N	\N
tenant_id	16	a07d36f8-7670-437e-a779-94ea5d03cbfa	8fc8bb6f-8f85-450c-9394-3aabcb0ce3ce	\N	\N	\N
tenant_id	17	7b907c28-59f0-4426-90f7-987972d93ee6	5c0e36e7-bbd7-4266-950c-7a2ab0acc86d	\N	\N	\N
tenant_id	16	b036bf2e-b3bd-4ee4-b23b-c030512ef85f	354b339b-0698-4075-9a1c-bf8f5691d881	\N	\N	\N
tenant_id	16	bcd0a536-7bb1-4203-b198-cc62e64e04e2	86622a7d-ebba-4c8f-8e4a-94a37242bce7	\N	\N	\N
tenant_id	16	bd84ff87-df12-4e7c-92f1-81172429c43f	94f82c45-796b-461b-ac6f-8357e305c449	\N	\N	\N
tenant_id	16	8e539a09-5348-4db0-8ee2-3308ad684eb0	6bfd33b7-8c34-45b0-ba25-14736c1dbbc5	\N	\N	\N
tenant_id	16	5291b7d6-21a1-418a-8814-1f01c9f5a945	e1cebdc4-b9ee-4abb-9862-a48d20089072	\N	\N	\N
tenant_id	25	e35952af-e25b-4aa6-9ab4-61ea47c53dfc	fbe60a1a-e6bd-4985-b12a-922fd40bfcc1	\N	\N	\N
tenant_id	27	9f7dbe19-063f-4a00-aa72-6dafa5922eed	dcdbf11e-978b-4301-87c1-f04cf9d28eb8	\N	\N	\N
must_change_password	false	9f7dbe19-063f-4a00-aa72-6dafa5922eed	313630c5-b2f1-47a6-a7c5-32223b927d87	\N	\N	\N
tenant_id	27	0db58312-8a2b-4f6d-b418-269a52584d94	3e9da825-8983-4118-83db-439196f2e8d9	\N	\N	\N
must_change_password	false	0db58312-8a2b-4f6d-b418-269a52584d94	d12a1cab-a4e1-4f9c-88ff-db3658e4b4d6	\N	\N	\N
tenant_id	27	1851bee9-fc26-4e43-82f7-59d50d4d7b45	44b376c8-9f50-4c2f-acfe-ea9053e7124b	\N	\N	\N
must_change_password	false	1851bee9-fc26-4e43-82f7-59d50d4d7b45	9c93fb38-b9b9-4b91-a0e3-8e8b8df56c14	\N	\N	\N
tenant_id	16	79eda9a9-c697-476e-be1d-03aaad8f7297	f192b5e1-f876-4cf0-8613-2c51ebe757c0	\N	\N	\N
must_change_password	false	79eda9a9-c697-476e-be1d-03aaad8f7297	1878b33e-6581-42bf-9e71-82dcfde552b9	\N	\N	\N
tenant_id	16	fc05c939-7e24-4cfc-90b5-3ecd74572b39	202adcb5-0aad-484e-b8d4-9beec109989b	\N	\N	\N
must_change_password	false	fc05c939-7e24-4cfc-90b5-3ecd74572b39	e1d9af65-41ef-45f2-8bc4-4439d1948488	\N	\N	\N
tenant_id	28	73993873-ef95-4074-843b-ffd111c6754b	ee8483d0-54d2-41e3-b454-e4179628725d	\N	\N	\N
must_change_password	false	73993873-ef95-4074-843b-ffd111c6754b	e11397e1-85f4-461b-857c-69e7e682d5d9	\N	\N	\N
tenant_id	28	1f5e14d8-4595-4946-971c-3815a1590104	81d16380-f347-430e-8fee-6a492988282f	\N	\N	\N
tenant_id	28	fce143b9-3164-4e97-b49f-b7a39e448e35	168f340c-153e-4e19-9865-b4b7b520dc00	\N	\N	\N
must_change_password	true	11faae6a-09c9-4778-b864-0b015407cb8c	a76702b6-7c56-4abb-8a06-3d5ace466a60	\N	\N	\N
tenant_id	29	11faae6a-09c9-4778-b864-0b015407cb8c	a38b5d24-353a-45f3-bfea-da9e6504f169	\N	\N	\N
must_change_password	true	7d5d0a38-2de6-460c-a14b-e735442f710d	7c24e536-463b-4242-b925-3a406b370ed1	\N	\N	\N
tenant_id	32	7d5d0a38-2de6-460c-a14b-e735442f710d	fb2d7ea9-4c56-4418-9fa6-ff02396fd9ca	\N	\N	\N
tenant_id	33	6cd9b564-fdc7-401f-bda1-b371a5062dbc	ea3509a2-9561-4602-a083-30bd8368246c	\N	\N	\N
must_change_password	false	6cd9b564-fdc7-401f-bda1-b371a5062dbc	bb8006ec-e302-4dec-ab8f-8de66157211d	\N	\N	\N
tenant_id	28	41e4bdb8-f0dc-4351-9c33-54fd227f69bf	9ed3a1da-9ce5-48f0-9c63-56ecb153edd6	\N	\N	\N
must_change_password	false	41e4bdb8-f0dc-4351-9c33-54fd227f69bf	b212e440-d4a1-499c-a8f1-6f7e74412dcd	\N	\N	\N
must_change_password	true	41123d37-da94-424e-b20b-3dce730679b1	b4cb21fb-6379-4283-adb1-b9997a3cef06	\N	\N	\N
tenant_id	35	41123d37-da94-424e-b20b-3dce730679b1	d89c4a0c-9235-411b-8217-84d4943b8a03	\N	\N	\N
tenant_id	28	cba32bca-7c95-41a9-93c7-b7693f7b11ec	4fcdfe43-e8f5-43ab-82c8-d185cc53a07d	\N	\N	\N
must_change_password	false	cba32bca-7c95-41a9-93c7-b7693f7b11ec	72b74212-f1b1-4e79-aa87-7efd0e9e0fdc	\N	\N	\N
must_change_password	false	fce143b9-3164-4e97-b49f-b7a39e448e35	5014dbbd-e933-46be-9b44-0c79f0882ff8	\N	\N	\N
must_change_password	true	9c4925e9-d004-4976-a024-8738729c17a6	a0d59271-1846-4290-8f0a-f68d20e87a55	\N	\N	\N
tenant_id	28	9c4925e9-d004-4976-a024-8738729c17a6	c1d79381-9c8d-46ec-b538-0a89f0b4b0a1	\N	\N	\N
tenant_id	system	d0863ab4-7151-4f89-be1e-11bdbb5f88e0	1408900c-40c2-43c8-9065-49686c061b98	\N	\N	\N
must_change_password	true	333079f6-fb21-494a-a789-7466d6afb6f3	d37e074a-dcec-4b50-8568-79e0e149e862	\N	\N	\N
tenant_id	28	333079f6-fb21-494a-a789-7466d6afb6f3	00bfad3e-f470-436b-a125-9771005ae648	\N	\N	\N
tenant_id	28	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6	3a145deb-138f-467e-8ee1-a2508a7a7b83	\N	\N	\N
must_change_password	false	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6	3b7bdf67-5cc8-4152-a02e-81fa2f8d4901	\N	\N	\N
must_change_password	false	1f5e14d8-4595-4946-971c-3815a1590104	8a3e6044-1e1b-4449-aa63-4d6aa16787c2	\N	\N	\N
must_change_password	true	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f	7967278e-8cff-44c2-a35c-9293843212c0	\N	\N	\N
tenant_id	28	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f	e79cf061-44a6-4eaf-b0ce-71f4ae256207	\N	\N	\N
must_change_password	true	fdb0ec96-f0b9-424d-a34e-7b4144979efa	0c37de47-6fcc-4734-96dc-c47e36637ec2	\N	\N	\N
tenant_id	36	fdb0ec96-f0b9-424d-a34e-7b4144979efa	d3366598-992e-4ca9-912d-347aee2fd34e	\N	\N	\N
must_change_password	true	13f38f7f-47cb-44c6-b50a-06121ea1e302	a4c1f689-fa7b-489f-8d99-853140901531	\N	\N	\N
tenant_id	28	13f38f7f-47cb-44c6-b50a-06121ea1e302	be046bb5-5263-4697-b5a0-7f9a2fb9a03f	\N	\N	\N
tenant_id	28	a68eb3cd-a728-46a2-bb12-098613cdbb14	08392363-ab38-4db2-931c-648bd31b9bda	\N	\N	\N
must_change_password	false	a68eb3cd-a728-46a2-bb12-098613cdbb14	aa0bf134-3b25-44bd-aa51-19002ad66531	\N	\N	\N
must_change_password	true	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f	2ff818b2-7ad2-4b71-8d43-6a35573d78e4	\N	\N	\N
tenant_id	37	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f	2e492698-6a43-44d0-9364-34f34cdaae2c	\N	\N	\N
must_change_password	true	ba7ae713-d025-4469-8d99-a29b0a7a4ae1	03cbccf2-28f5-43c9-88e3-1a6ed10b8a18	\N	\N	\N
tenant_id	38	ba7ae713-d025-4469-8d99-a29b0a7a4ae1	d979961c-4bd7-41ae-8e1d-145038c4891f	\N	\N	\N
must_change_password	true	5c45254c-a739-45f2-8b79-3cd95a3747dc	6eb8b8f1-ac1e-4b93-8b46-db9e5c8d2db6	\N	\N	\N
tenant_id	42	5c45254c-a739-45f2-8b79-3cd95a3747dc	50ada8fc-33e7-4c33-bc4e-6650e5764362	\N	\N	\N
must_change_password	true	c50545e4-81a9-40b2-b90a-c3b8a651b70c	273abd21-bbf1-4804-ad54-24eef1732ffb	\N	\N	\N
tenant_id	4	c50545e4-81a9-40b2-b90a-c3b8a651b70c	77ebe7a1-5bf0-4972-b0a3-f51a3ef6a7be	\N	\N	\N
tenant_id	1	fc4af818-eaaa-493d-9c30-e0fda104ca78	8c4eb77e-5a29-40b2-93d2-22e3cb0aa673	\N	\N	\N
tenant_id	2	2c722c17-455e-4786-b0cf-6e88f7a66f81	76443c14-de41-402b-adc7-41aed2698428	\N	\N	\N
must_change_password	true	e7861bcc-140a-4894-a5c6-0661900bc42b	ce58a737-899c-4633-a628-b94a556cf6cf	\N	\N	\N
tenant_id	1	e7861bcc-140a-4894-a5c6-0661900bc42b	295679bd-ccc3-4219-9cec-68d68bb9eaba	\N	\N	\N
must_change_password	true	9a683629-6c9e-403c-a7b9-ef683f2878f1	6ac8d85a-cd12-416d-8839-7da2ba2564a2	\N	\N	\N
tenant_id	1	9a683629-6c9e-403c-a7b9-ef683f2878f1	90a87bcb-1061-40a9-939a-e31ffcdd058f	\N	\N	\N
must_change_password	true	10921d32-7b89-46fa-ad2c-b8c9bfe2276b	0a4c3cb3-ac98-4b54-b132-b81c660ac786	\N	\N	\N
tenant_id	2	10921d32-7b89-46fa-ad2c-b8c9bfe2276b	e9819e84-2650-4c70-8323-450e20f34313	\N	\N	\N
tenant_id	2	6474a727-5e85-406d-9289-1fea2513c5da	d55df588-ba70-4e9c-8310-55ae2ad5fcf7	\N	\N	\N
must_change_password	false	6474a727-5e85-406d-9289-1fea2513c5da	21691cc1-1696-4bb8-ac8f-fb6a32fbf74f	\N	\N	\N
must_change_password	true	1177d06d-97dc-4483-a8a7-ed4819b4a450	0b261446-89da-46ef-bd43-fd0c208e4d83	\N	\N	\N
tenant_id	1	1177d06d-97dc-4483-a8a7-ed4819b4a450	39bec42a-8813-4861-9910-41aa436c31a9	\N	\N	\N
\.


--
-- Data for Name: user_consent; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_consent (id, client_id, user_id, created_date, last_updated_date, client_storage_provider, external_client_id) FROM stdin;
\.


--
-- Data for Name: user_consent_client_scope; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_consent_client_scope (user_consent_id, scope_id) FROM stdin;
\.


--
-- Data for Name: user_entity; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_entity (id, email, email_constraint, email_verified, enabled, federation_link, first_name, last_name, realm_id, username, created_timestamp, service_account_client_link, not_before) FROM stdin;
a411426c-8d55-462b-9a76-04c5ccabd118	\N	63ae7ece-2ac9-46f4-9b62-4701f8867f71	f	t	\N	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	admin	1782731436621	\N	0
60594911-2d3d-4cf6-9e30-55462c84415b	\N	91783045-a512-4f1e-9274-ff1a74244e71	f	t	\N	\N	\N	259bfe83-5374-4ae7-bb38-5464d0d5bc75	service-account-backend	1782732484489	bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	0
e40e7068-88e7-450c-8223-e0a2af4f209b	\N	daa02af1-7930-4e3f-92b5-071c37198a68	f	t	\N	\N	\N	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	service-account-smartschool-backend	1782817377670	2d441a2b-1316-4adc-b51a-8e2520fc33e6	0
8d240ffe-9a19-41e2-b5c6-e048b2b2ded4	aymen.bouraoui7@gmail.com	aymen.bouraoui7@gmail.com	t	t	\N	aymen	bouraoui	0dc4d0c8-9283-45d2-b43a-e6f1f41a6b26	aymen	1782818359176	\N	0
d497d997-554e-4c54-8ca9-4cc27f8560ed	\N	7db38b54-bead-4411-b3fa-da0086c20b29	f	t	\N	\N	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	service-account-smartschool-backend	1782818905865	5907c420-e9c0-4884-85ed-a753a9be228e	0
b0afe140-0b06-4065-9e40-bffe43e90b88	superadmin@smartschool.tn	superadmin@smartschool.tn	t	t	\N	Super	Admin	83e82497-9a85-4600-9b3d-877c38c4ff59	superadmin	1782818965837	\N	0
0f20480f-07ae-41b1-9c2a-eab9d4343ec6	admin.ecole@smartschool.tn	admin.ecole@smartschool.tn	t	t	\N	Ahmed	Ben Salem	83e82497-9a85-4600-9b3d-877c38c4ff59	admin.ecole	1782819529909	\N	0
fc4af818-eaaa-493d-9c30-e0fda104ca78	demo.ibn@smartschool.tn	demo.ibn@smartschool.tn	t	t	\N	Demo	Ibn	83e82497-9a85-4600-9b3d-877c38c4ff59	demo.ibn	1782994647947	\N	0
a27397a0-9714-4251-a9cb-fc6cdadd1c14	admin-repro-1784025597061@example.tn	admin-repro-1784025597061@example.tn	t	t	\N	Admin	Repro	83e82497-9a85-4600-9b3d-877c38c4ff59	admin.repro.1784025597061	1784025597492	\N	0
ec07d9dc-1d34-459a-afce-b20256b34bae	admin-repro-1784026316161@example.tn	admin-repro-1784026316161@example.tn	t	t	\N	Admin	Repro	83e82497-9a85-4600-9b3d-877c38c4ff59	admin.repro.1784026316161	1784026316549	\N	0
c597c98c-3ca9-4af7-a1cf-28a178a5d9f5	proviseur@lycee-e2e.tn	proviseur@lycee-e2e.tn	t	f	\N	Sonia	Proviseure	83e82497-9a85-4600-9b3d-877c38c4ff59	proviseur	1782994338889	\N	0
a07d36f8-7670-437e-a779-94ea5d03cbfa	zikou@gmail.com	zikou@gmail.com	t	t	\N	zakaria	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	zikou	1784112799986	\N	0
7b907c28-59f0-4426-90f7-987972d93ee6	moeiz@gmail.com	moeiz@gmail.com	t	t	\N	moeiz	laouini	83e82497-9a85-4600-9b3d-877c38c4ff59	moeiz	1784362776663	\N	0
b036bf2e-b3bd-4ee4-b23b-c030512ef85f	sa444@gmail.com	sa444@gmail.com	t	t	\N	aymen	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	sa444	1784391144617	\N	0
9f7dbe19-063f-4a00-aa72-6dafa5922eed	aymen@gmail.com	aymen@gmail.com	t	t	\N	aymen	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	aymen	1784990659220	\N	0
bd84ff87-df12-4e7c-92f1-81172429c43f	7za@gmail.com	7za@gmail.com	t	t	\N	bouraoui	soulaiman	83e82497-9a85-4600-9b3d-877c38c4ff59	7za	1784454905204	\N	0
bcd0a536-7bb1-4203-b198-cc62e64e04e2	saaaaa@hotmail.com	saaaaa@hotmail.com	t	t	\N	sber	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	saaaaa	1784391201850	\N	0
8e539a09-5348-4db0-8ee2-3308ad684eb0	s784aa@gmail.com	s784aa@gmail.com	t	t	\N	bouraoui	yakin	83e82497-9a85-4600-9b3d-877c38c4ff59	s784aa	1784455271134	\N	0
5291b7d6-21a1-418a-8814-1f01c9f5a945	7z7a1@gmail.com	7z7a1@gmail.com	t	t	\N	bouraoui	radhia	83e82497-9a85-4600-9b3d-877c38c4ff59	7z7a1	1784455300130	\N	0
e35952af-e25b-4aa6-9ab4-61ea47c53dfc	aymen.bouraoui7@gmail.com	aymen.bouraoui7@gmail.com	t	t	\N	aymen	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	aymen.bouraoui7	1784989718750	\N	0
0db58312-8a2b-4f6d-b418-269a52584d94	mohamed@gmail.com	mohamed@gmail.com	t	t	\N	Ben	Ali mohamed	83e82497-9a85-4600-9b3d-877c38c4ff59	mohamed	1784990954077	\N	0
1851bee9-fc26-4e43-82f7-59d50d4d7b45	khouloud@gmail.com	khouloud@gmail.com	t	t	\N	khouloud	salhi	83e82497-9a85-4600-9b3d-877c38c4ff59	khouloud	1784991127312	\N	0
79eda9a9-c697-476e-be1d-03aaad8f7297	m@gmail.com	m@gmail.com	t	t	\N	Ben	Ali Mohamed	83e82497-9a85-4600-9b3d-877c38c4ff59	m.ben.ali.mohamed	1785109498726	\N	0
fc05c939-7e24-4cfc-90b5-3ecd74572b39	mm@gmail.com	mm@gmail.com	t	t	\N	Bouraoui	Mohamed	83e82497-9a85-4600-9b3d-877c38c4ff59	mm.bouraoui.mohamed	1785195082336	\N	0
73993873-ef95-4074-843b-ffd111c6754b	admin@seed-test.tn	admin@seed-test.tn	t	t	\N	Admin	Seed Test	83e82497-9a85-4600-9b3d-877c38c4ff59	admin	1785197594137	\N	0
fce143b9-3164-4e97-b49f-b7a39e448e35	aya.sahli@ecole.tn	aya.sahli@ecole.tn	t	t	\N	Sahli	Aya	83e82497-9a85-4600-9b3d-877c38c4ff59	aya.sahli	1785282744398	\N	0
11faae6a-09c9-4778-b864-0b015407cb8c	aymen90@gmail.com	aymen90@gmail.com	t	t	\N	aymen	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	aymen90	1785365737973	\N	0
6cd9b564-fdc7-401f-bda1-b371a5062dbc	k@gmail.com	k@gmail.com	t	t	\N	khouloud	salhi	83e82497-9a85-4600-9b3d-877c38c4ff59	k.khouloud.salhi	1785366806885	\N	0
41e4bdb8-f0dc-4351-9c33-54fd227f69bf	nour.bouassida@ecole.tn	nour.bouassida@ecole.tn	t	t	\N	Bouassida	Nour	83e82497-9a85-4600-9b3d-877c38c4ff59	nour.bouassida	1785537807428	\N	0
41123d37-da94-424e-b20b-3dce730679b1	moeiz78@gmail.com	moeiz78@gmail.com	t	t	\N	aymen	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	moeiz78	1785706515883	\N	0
cba32bca-7c95-41a9-93c7-b7693f7b11ec	youssef.sfaxi@ecole.tn	youssef.sfaxi@ecole.tn	t	t	\N	Sfaxi	Youssef	83e82497-9a85-4600-9b3d-877c38c4ff59	youssef.sfaxi	1785707131071	\N	0
d0863ab4-7151-4f89-be1e-11bdbb5f88e0	test.monitoring@smartschool.tn	test.monitoring@smartschool.tn	t	t	\N	Test	Monitoring	83e82497-9a85-4600-9b3d-877c38c4ff59	test.monitoring	1787036363485	\N	0
333079f6-fb21-494a-a789-7466d6afb6f3	ghofrane.mabrouk@ecole.tn	ghofrane.mabrouk@ecole.tn	t	t	\N	Mabrouk	Ghofrane	83e82497-9a85-4600-9b3d-877c38c4ff59	ghofrane.mabrouk	1787387680989	\N	0
25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6	mehdi.ben-ali@ecole.tn	mehdi.ben-ali@ecole.tn	t	t	\N	Ben	Ali Mehdi	83e82497-9a85-4600-9b3d-877c38c4ff59	mehdi.ben.ali	1787388348483	\N	0
1f5e14d8-4595-4946-971c-3815a1590104	nour.dhaouadi@ecole.tn	nour.dhaouadi@ecole.tn	t	t	\N	Dhaouadi	Nour	83e82497-9a85-4600-9b3d-877c38c4ff59	nour.dhaouadi	1785200788250	\N	0
7d5d0a38-2de6-460c-a14b-e735442f710d	aymen9@gmail.com	aymen9@gmail.com	t	f	\N	aymen	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	aymen9	1785366399425	\N	0
b03867b6-332e-4a47-99cf-41f77263429e	directeur@college-e2e.tn	directeur@college-e2e.tn	t	t	\N	Karim	Directeur	83e82497-9a85-4600-9b3d-877c38c4ff59	directeur	1782993930610	\N	0
9c4925e9-d004-4976-a024-8738729c17a6	ah@gmail.com	ah@gmail.com	t	t	\N	ahmed	hammami	83e82497-9a85-4600-9b3d-877c38c4ff59	ah.ahmed.hammami	1786275235936	\N	0
cf1e2c86-f8d9-4b38-99be-a30b08a79c2f	mehdi.bouazizi@ecole.tn	mehdi.bouazizi@ecole.tn	t	t	\N	Bouazizi	Mehdi	83e82497-9a85-4600-9b3d-877c38c4ff59	mehdi.bouazizi	1787395447540	\N	0
fdb0ec96-f0b9-424d-a34e-7b4144979efa	sadek@gmail.comm	sadek@gmail.comm	t	t	\N	sadek	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	sadek	1787397950684	\N	0
13f38f7f-47cb-44c6-b50a-06121ea1e302	omar.guesmi@ecole.tn	omar.guesmi@ecole.tn	t	t	\N	Guesmi	Omar	83e82497-9a85-4600-9b3d-877c38c4ff59	omar.guesmi	1787400855056	\N	0
a68eb3cd-a728-46a2-bb12-098613cdbb14	a90@gmail.com	a90@gmail.com	t	t	\N	aymen	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	a90	1787893348804	\N	0
2c722c17-455e-4786-b0cf-6e88f7a66f81	demo.carthage@smartschool.tn	demo.carthage@smartschool.tn	t	t	\N	Demo	Carthage	83e82497-9a85-4600-9b3d-877c38c4ff59	demo.carthage	1788011108580	\N	0
f117e0b8-fcec-4a2b-abc7-c3ef6e25050f	aymen999@gmail.com	aymen999@gmail.com	t	t	\N	aymen	bouraoui	83e82497-9a85-4600-9b3d-877c38c4ff59	aymen999	1788299552635	\N	0
ba7ae713-d025-4469-8d99-a29b0a7a4ae1	alsalhikhouloud@gmail.com	alsalhikhouloud@gmail.com	t	t	\N	khouloud	salhi	83e82497-9a85-4600-9b3d-877c38c4ff59	alsalhikhouloud	1788905008424	\N	0
5c45254c-a739-45f2-8b79-3cd95a3747dc	khouloud.salhi@tessi.fr	khouloud.salhi@tessi.fr	t	t	\N	khouloud	salhi	83e82497-9a85-4600-9b3d-877c38c4ff59	khouloud.salhi	1788905535329	\N	0
c50545e4-81a9-40b2-b90a-c3b8a651b70c	ah4155@gmail.com	ah4155@gmail.com	t	t	\N	aymen	\N	83e82497-9a85-4600-9b3d-877c38c4ff59	ah4155	1788907317274	\N	0
e7861bcc-140a-4894-a5c6-0661900bc42b	ghada.zaied.ibn-ar-01@ibn.edu.tn	ghada.zaied.ibn-ar-01@ibn.edu.tn	t	t	\N	Zaied	Ghada	83e82497-9a85-4600-9b3d-877c38c4ff59	ghada.zaied.ibn.ar.01	1788964975980	\N	0
9a683629-6c9e-403c-a7b9-ef683f2878f1	nizar.chouchane.ibn-ar-04@ibn.edu.tn	nizar.chouchane.ibn-ar-04@ibn.edu.tn	t	t	\N	Chouchane	Nizar	83e82497-9a85-4600-9b3d-877c38c4ff59	nizar.chouchane.ibn.ar.04	1788965319969	\N	0
10921d32-7b89-46fa-ad2c-b8c9bfe2276b	hedi.bensalah.car-ar-01@car.edu.tn	hedi.bensalah.car-ar-01@car.edu.tn	t	t	\N	Ben	Salah Hedi	83e82497-9a85-4600-9b3d-877c38c4ff59	hedi.bensalah.car.ar.01	1788995222310	\N	0
6474a727-5e85-406d-9289-1fea2513c5da	ah89@gmail.com	ah89@gmail.com	t	t	\N	ahmed	hammami	83e82497-9a85-4600-9b3d-877c38c4ff59	ah89	1788995817979	\N	0
1177d06d-97dc-4483-a8a7-ed4819b4a450	imen.dhaouadi.ibn-ar-05@ibn.edu.tn	imen.dhaouadi.ibn-ar-05@ibn.edu.tn	t	t	\N	Dhaouadi	Imen	83e82497-9a85-4600-9b3d-877c38c4ff59	imen.dhaouadi.ibn.ar.05	1789078961347	\N	0
\.


--
-- Data for Name: user_federation_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_federation_config (user_federation_provider_id, value, name) FROM stdin;
\.


--
-- Data for Name: user_federation_mapper; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_federation_mapper (id, name, federation_provider_id, federation_mapper_type, realm_id) FROM stdin;
\.


--
-- Data for Name: user_federation_mapper_config; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_federation_mapper_config (user_federation_mapper_id, value, name) FROM stdin;
\.


--
-- Data for Name: user_federation_provider; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_federation_provider (id, changed_sync_period, display_name, full_sync_period, last_sync, priority, provider_name, realm_id) FROM stdin;
\.


--
-- Data for Name: user_group_membership; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_group_membership (group_id, user_id, membership_type) FROM stdin;
e3895f1d-6beb-4fa2-a490-ffe89c238b09	73993873-ef95-4074-843b-ffd111c6754b	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	1f5e14d8-4595-4946-971c-3815a1590104	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	fce143b9-3164-4e97-b49f-b7a39e448e35	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	41e4bdb8-f0dc-4351-9c33-54fd227f69bf	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	cba32bca-7c95-41a9-93c7-b7693f7b11ec	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	9c4925e9-d004-4976-a024-8738729c17a6	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	333079f6-fb21-494a-a789-7466d6afb6f3	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	13f38f7f-47cb-44c6-b50a-06121ea1e302	UNMANAGED
e3895f1d-6beb-4fa2-a490-ffe89c238b09	a68eb3cd-a728-46a2-bb12-098613cdbb14	UNMANAGED
b78c37f3-35d3-4196-8087-d0050246de57	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f	UNMANAGED
b78c37f3-35d3-4196-8087-d0050246de57	9f7dbe19-063f-4a00-aa72-6dafa5922eed	UNMANAGED
062753ef-cfdc-49df-b1e2-7c0334294fc8	fc4af818-eaaa-493d-9c30-e0fda104ca78	UNMANAGED
6c5a130b-6bbb-4245-be3d-5fe648d86e95	2c722c17-455e-4786-b0cf-6e88f7a66f81	UNMANAGED
062753ef-cfdc-49df-b1e2-7c0334294fc8	c50545e4-81a9-40b2-b90a-c3b8a651b70c	UNMANAGED
c8e2cf10-45e0-4ec8-acb5-5534a258d304	e7861bcc-140a-4894-a5c6-0661900bc42b	UNMANAGED
c8e2cf10-45e0-4ec8-acb5-5534a258d304	9a683629-6c9e-403c-a7b9-ef683f2878f1	UNMANAGED
6c5a130b-6bbb-4245-be3d-5fe648d86e95	10921d32-7b89-46fa-ad2c-b8c9bfe2276b	UNMANAGED
6c5a130b-6bbb-4245-be3d-5fe648d86e95	6474a727-5e85-406d-9289-1fea2513c5da	UNMANAGED
c8e2cf10-45e0-4ec8-acb5-5534a258d304	1177d06d-97dc-4483-a8a7-ed4819b4a450	UNMANAGED
\.


--
-- Data for Name: user_required_action; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_required_action (user_id, required_action) FROM stdin;
\.


--
-- Data for Name: user_role_mapping; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.user_role_mapping (role_id, user_id) FROM stdin;
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	a411426c-8d55-462b-9a76-04c5ccabd118
5650b984-c6a8-4360-86c3-164318770281	a411426c-8d55-462b-9a76-04c5ccabd118
1abec7ba-4d9e-4b1c-96b6-89bd6a10416f	60594911-2d3d-4cf6-9e30-55462c84415b
24addff9-15ea-411f-ab2a-e4f293f19fb2	60594911-2d3d-4cf6-9e30-55462c84415b
3209610d-c3e0-478f-9e69-5078e51fa2e2	e40e7068-88e7-450c-8223-e0a2af4f209b
869e58f7-8fe9-4381-bfd0-698985e90aa2	e40e7068-88e7-450c-8223-e0a2af4f209b
894a6f13-677e-4652-91f9-bd966c7ce297	e40e7068-88e7-450c-8223-e0a2af4f209b
9fd49d77-d3ff-4cb4-b6b3-5b2f13d76036	e40e7068-88e7-450c-8223-e0a2af4f209b
8645a0c6-bac5-406b-a652-c11ed62d3218	e40e7068-88e7-450c-8223-e0a2af4f209b
3209610d-c3e0-478f-9e69-5078e51fa2e2	8d240ffe-9a19-41e2-b5c6-e048b2b2ded4
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	d497d997-554e-4c54-8ca9-4cc27f8560ed
aaf983d6-da6a-4032-a445-7155a42ab9c8	d497d997-554e-4c54-8ca9-4cc27f8560ed
0b9d741d-26f4-472b-9961-cd88381531a0	d497d997-554e-4c54-8ca9-4cc27f8560ed
99df3929-ad70-4232-93ce-34b18da7885a	d497d997-554e-4c54-8ca9-4cc27f8560ed
71b2ddf7-0195-4f4a-88ec-d4dc4d34ec62	d497d997-554e-4c54-8ca9-4cc27f8560ed
fe043124-aa91-4aa0-9746-dc1c8c425535	d497d997-554e-4c54-8ca9-4cc27f8560ed
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	b0afe140-0b06-4065-9e40-bffe43e90b88
f6c0b2dc-c757-4ef4-9e1d-a7ec9eef8717	b0afe140-0b06-4065-9e40-bffe43e90b88
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	0f20480f-07ae-41b1-9c2a-eab9d4343ec6
ce68eb42-579b-4fee-9f03-cd19d8822458	0f20480f-07ae-41b1-9c2a-eab9d4343ec6
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	b03867b6-332e-4a47-99cf-41f77263429e
ce68eb42-579b-4fee-9f03-cd19d8822458	b03867b6-332e-4a47-99cf-41f77263429e
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	c597c98c-3ca9-4af7-a1cf-28a178a5d9f5
ce68eb42-579b-4fee-9f03-cd19d8822458	c597c98c-3ca9-4af7-a1cf-28a178a5d9f5
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	fc4af818-eaaa-493d-9c30-e0fda104ca78
ce68eb42-579b-4fee-9f03-cd19d8822458	fc4af818-eaaa-493d-9c30-e0fda104ca78
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	a27397a0-9714-4251-a9cb-fc6cdadd1c14
ce68eb42-579b-4fee-9f03-cd19d8822458	a27397a0-9714-4251-a9cb-fc6cdadd1c14
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	ec07d9dc-1d34-459a-afce-b20256b34bae
ce68eb42-579b-4fee-9f03-cd19d8822458	ec07d9dc-1d34-459a-afce-b20256b34bae
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	a07d36f8-7670-437e-a779-94ea5d03cbfa
ce68eb42-579b-4fee-9f03-cd19d8822458	a07d36f8-7670-437e-a779-94ea5d03cbfa
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	7b907c28-59f0-4426-90f7-987972d93ee6
ce68eb42-579b-4fee-9f03-cd19d8822458	7b907c28-59f0-4426-90f7-987972d93ee6
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	b036bf2e-b3bd-4ee4-b23b-c030512ef85f
412b35ac-a689-4cb7-b871-a72d353c28e7	b036bf2e-b3bd-4ee4-b23b-c030512ef85f
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	bcd0a536-7bb1-4203-b198-cc62e64e04e2
945735ea-5cc5-4fae-835b-3109128766b1	bcd0a536-7bb1-4203-b198-cc62e64e04e2
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	bd84ff87-df12-4e7c-92f1-81172429c43f
412b35ac-a689-4cb7-b871-a72d353c28e7	bd84ff87-df12-4e7c-92f1-81172429c43f
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	8e539a09-5348-4db0-8ee2-3308ad684eb0
412b35ac-a689-4cb7-b871-a72d353c28e7	8e539a09-5348-4db0-8ee2-3308ad684eb0
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	5291b7d6-21a1-418a-8814-1f01c9f5a945
412b35ac-a689-4cb7-b871-a72d353c28e7	5291b7d6-21a1-418a-8814-1f01c9f5a945
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	e35952af-e25b-4aa6-9ab4-61ea47c53dfc
ce68eb42-579b-4fee-9f03-cd19d8822458	e35952af-e25b-4aa6-9ab4-61ea47c53dfc
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	9f7dbe19-063f-4a00-aa72-6dafa5922eed
ce68eb42-579b-4fee-9f03-cd19d8822458	9f7dbe19-063f-4a00-aa72-6dafa5922eed
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	0db58312-8a2b-4f6d-b418-269a52584d94
412b35ac-a689-4cb7-b871-a72d353c28e7	0db58312-8a2b-4f6d-b418-269a52584d94
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	1851bee9-fc26-4e43-82f7-59d50d4d7b45
945735ea-5cc5-4fae-835b-3109128766b1	1851bee9-fc26-4e43-82f7-59d50d4d7b45
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	79eda9a9-c697-476e-be1d-03aaad8f7297
412b35ac-a689-4cb7-b871-a72d353c28e7	79eda9a9-c697-476e-be1d-03aaad8f7297
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	fc05c939-7e24-4cfc-90b5-3ecd74572b39
412b35ac-a689-4cb7-b871-a72d353c28e7	fc05c939-7e24-4cfc-90b5-3ecd74572b39
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	73993873-ef95-4074-843b-ffd111c6754b
ce68eb42-579b-4fee-9f03-cd19d8822458	73993873-ef95-4074-843b-ffd111c6754b
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	1f5e14d8-4595-4946-971c-3815a1590104
412b35ac-a689-4cb7-b871-a72d353c28e7	1f5e14d8-4595-4946-971c-3815a1590104
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	fce143b9-3164-4e97-b49f-b7a39e448e35
412b35ac-a689-4cb7-b871-a72d353c28e7	fce143b9-3164-4e97-b49f-b7a39e448e35
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	11faae6a-09c9-4778-b864-0b015407cb8c
ce68eb42-579b-4fee-9f03-cd19d8822458	11faae6a-09c9-4778-b864-0b015407cb8c
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	7d5d0a38-2de6-460c-a14b-e735442f710d
ce68eb42-579b-4fee-9f03-cd19d8822458	7d5d0a38-2de6-460c-a14b-e735442f710d
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	6cd9b564-fdc7-401f-bda1-b371a5062dbc
ce68eb42-579b-4fee-9f03-cd19d8822458	6cd9b564-fdc7-401f-bda1-b371a5062dbc
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	41e4bdb8-f0dc-4351-9c33-54fd227f69bf
412b35ac-a689-4cb7-b871-a72d353c28e7	41e4bdb8-f0dc-4351-9c33-54fd227f69bf
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	41123d37-da94-424e-b20b-3dce730679b1
ce68eb42-579b-4fee-9f03-cd19d8822458	41123d37-da94-424e-b20b-3dce730679b1
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	cba32bca-7c95-41a9-93c7-b7693f7b11ec
412b35ac-a689-4cb7-b871-a72d353c28e7	cba32bca-7c95-41a9-93c7-b7693f7b11ec
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	9c4925e9-d004-4976-a024-8738729c17a6
945735ea-5cc5-4fae-835b-3109128766b1	9c4925e9-d004-4976-a024-8738729c17a6
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	d0863ab4-7151-4f89-be1e-11bdbb5f88e0
f6c0b2dc-c757-4ef4-9e1d-a7ec9eef8717	d0863ab4-7151-4f89-be1e-11bdbb5f88e0
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	333079f6-fb21-494a-a789-7466d6afb6f3
412b35ac-a689-4cb7-b871-a72d353c28e7	333079f6-fb21-494a-a789-7466d6afb6f3
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6
412b35ac-a689-4cb7-b871-a72d353c28e7	25bfa14b-90e5-4acd-b6cd-1e0c581b5bf6
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f
412b35ac-a689-4cb7-b871-a72d353c28e7	cf1e2c86-f8d9-4b38-99be-a30b08a79c2f
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	fdb0ec96-f0b9-424d-a34e-7b4144979efa
ce68eb42-579b-4fee-9f03-cd19d8822458	fdb0ec96-f0b9-424d-a34e-7b4144979efa
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	13f38f7f-47cb-44c6-b50a-06121ea1e302
412b35ac-a689-4cb7-b871-a72d353c28e7	13f38f7f-47cb-44c6-b50a-06121ea1e302
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	a68eb3cd-a728-46a2-bb12-098613cdbb14
945735ea-5cc5-4fae-835b-3109128766b1	a68eb3cd-a728-46a2-bb12-098613cdbb14
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	2c722c17-455e-4786-b0cf-6e88f7a66f81
ce68eb42-579b-4fee-9f03-cd19d8822458	2c722c17-455e-4786-b0cf-6e88f7a66f81
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f
ce68eb42-579b-4fee-9f03-cd19d8822458	f117e0b8-fcec-4a2b-abc7-c3ef6e25050f
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	ba7ae713-d025-4469-8d99-a29b0a7a4ae1
ce68eb42-579b-4fee-9f03-cd19d8822458	ba7ae713-d025-4469-8d99-a29b0a7a4ae1
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	5c45254c-a739-45f2-8b79-3cd95a3747dc
ce68eb42-579b-4fee-9f03-cd19d8822458	5c45254c-a739-45f2-8b79-3cd95a3747dc
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	c50545e4-81a9-40b2-b90a-c3b8a651b70c
945735ea-5cc5-4fae-835b-3109128766b1	c50545e4-81a9-40b2-b90a-c3b8a651b70c
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	e7861bcc-140a-4894-a5c6-0661900bc42b
412b35ac-a689-4cb7-b871-a72d353c28e7	e7861bcc-140a-4894-a5c6-0661900bc42b
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	9a683629-6c9e-403c-a7b9-ef683f2878f1
412b35ac-a689-4cb7-b871-a72d353c28e7	9a683629-6c9e-403c-a7b9-ef683f2878f1
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	10921d32-7b89-46fa-ad2c-b8c9bfe2276b
412b35ac-a689-4cb7-b871-a72d353c28e7	10921d32-7b89-46fa-ad2c-b8c9bfe2276b
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	6474a727-5e85-406d-9289-1fea2513c5da
945735ea-5cc5-4fae-835b-3109128766b1	6474a727-5e85-406d-9289-1fea2513c5da
da6d1a2a-e1a0-45b5-b55b-e0a5b6a40c1e	1177d06d-97dc-4483-a8a7-ed4819b4a450
412b35ac-a689-4cb7-b871-a72d353c28e7	1177d06d-97dc-4483-a8a7-ed4819b4a450
\.


--
-- Data for Name: username_login_failure; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.username_login_failure (realm_id, username, failed_login_not_before, last_failure, last_ip_failure, num_failures) FROM stdin;
\.


--
-- Data for Name: web_origins; Type: TABLE DATA; Schema: public; Owner: keycloak
--

COPY public.web_origins (client_id, value) FROM stdin;
7d8dc3b7-5ef8-42ad-8f3e-43a7617b3311	+
bdc1c671-1f99-4c08-9b69-6c47e33a6ea8	/*
cbcbcbde-a715-4358-a3c3-7f027c4436b6	+
2d441a2b-1316-4adc-b51a-8e2520fc33e6	/*
81b24093-5b2e-4796-9adc-970cd6e97d98	/*
d5442a0f-3191-407b-befa-391aaa28773d	+
2e5dede9-f6b2-489c-8c2b-44ca02f30878	http://localhost:3001
2e5dede9-f6b2-489c-8c2b-44ca02f30878	http://localhost:3000
\.


--
-- Name: username_login_failure CONSTRAINT_17-2; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.username_login_failure
    ADD CONSTRAINT "CONSTRAINT_17-2" PRIMARY KEY (realm_id, username);


--
-- Name: org_domain ORG_DOMAIN_pkey; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.org_domain
    ADD CONSTRAINT "ORG_DOMAIN_pkey" PRIMARY KEY (id, name);


--
-- Name: org ORG_pkey; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT "ORG_pkey" PRIMARY KEY (id);


--
-- Name: keycloak_role UK_J3RWUVD56ONTGSUHOGM184WW2-2; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT "UK_J3RWUVD56ONTGSUHOGM184WW2-2" UNIQUE (name, client_realm_constraint);


--
-- Name: client_auth_flow_bindings c_cli_flow_bind; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_auth_flow_bindings
    ADD CONSTRAINT c_cli_flow_bind PRIMARY KEY (client_id, binding_name);


--
-- Name: client_scope_client c_cli_scope_bind; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope_client
    ADD CONSTRAINT c_cli_scope_bind PRIMARY KEY (client_id, scope_id);


--
-- Name: client_initial_access cnstr_client_init_acc_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_initial_access
    ADD CONSTRAINT cnstr_client_init_acc_pk PRIMARY KEY (id);


--
-- Name: realm_default_groups con_group_id_def_groups; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT con_group_id_def_groups UNIQUE (group_id);


--
-- Name: broker_link constr_broker_link_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.broker_link
    ADD CONSTRAINT constr_broker_link_pk PRIMARY KEY (identity_provider, user_id);


--
-- Name: component_config constr_component_config_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.component_config
    ADD CONSTRAINT constr_component_config_pk PRIMARY KEY (id);


--
-- Name: component constr_component_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT constr_component_pk PRIMARY KEY (id);


--
-- Name: fed_user_required_action constr_fed_required_action; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_required_action
    ADD CONSTRAINT constr_fed_required_action PRIMARY KEY (required_action, user_id);


--
-- Name: fed_user_attribute constr_fed_user_attr_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_attribute
    ADD CONSTRAINT constr_fed_user_attr_pk PRIMARY KEY (id);


--
-- Name: fed_user_consent constr_fed_user_consent_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_consent
    ADD CONSTRAINT constr_fed_user_consent_pk PRIMARY KEY (id);


--
-- Name: fed_user_credential constr_fed_user_cred_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_credential
    ADD CONSTRAINT constr_fed_user_cred_pk PRIMARY KEY (id);


--
-- Name: fed_user_group_membership constr_fed_user_group; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_group_membership
    ADD CONSTRAINT constr_fed_user_group PRIMARY KEY (group_id, user_id);


--
-- Name: fed_user_role_mapping constr_fed_user_role; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_role_mapping
    ADD CONSTRAINT constr_fed_user_role PRIMARY KEY (role_id, user_id);


--
-- Name: federated_user constr_federated_user; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.federated_user
    ADD CONSTRAINT constr_federated_user PRIMARY KEY (id);


--
-- Name: realm_default_groups constr_realm_default_groups; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT constr_realm_default_groups PRIMARY KEY (realm_id, group_id);


--
-- Name: realm_enabled_event_types constr_realm_enabl_event_types; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_enabled_event_types
    ADD CONSTRAINT constr_realm_enabl_event_types PRIMARY KEY (realm_id, value);


--
-- Name: realm_events_listeners constr_realm_events_listeners; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_events_listeners
    ADD CONSTRAINT constr_realm_events_listeners PRIMARY KEY (realm_id, value);


--
-- Name: realm_supported_locales constr_realm_supported_locales; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_supported_locales
    ADD CONSTRAINT constr_realm_supported_locales PRIMARY KEY (realm_id, value);


--
-- Name: identity_provider constraint_2b; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT constraint_2b PRIMARY KEY (internal_id);


--
-- Name: client_attributes constraint_3c; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_attributes
    ADD CONSTRAINT constraint_3c PRIMARY KEY (client_id, name);


--
-- Name: event_entity constraint_4; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.event_entity
    ADD CONSTRAINT constraint_4 PRIMARY KEY (id);


--
-- Name: federated_identity constraint_40; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.federated_identity
    ADD CONSTRAINT constraint_40 PRIMARY KEY (identity_provider, user_id);


--
-- Name: realm constraint_4a; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm
    ADD CONSTRAINT constraint_4a PRIMARY KEY (id);


--
-- Name: user_federation_provider constraint_5c; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_provider
    ADD CONSTRAINT constraint_5c PRIMARY KEY (id);


--
-- Name: client constraint_7; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client
    ADD CONSTRAINT constraint_7 PRIMARY KEY (id);


--
-- Name: scope_mapping constraint_81; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.scope_mapping
    ADD CONSTRAINT constraint_81 PRIMARY KEY (client_id, role_id);


--
-- Name: client_node_registrations constraint_84; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_node_registrations
    ADD CONSTRAINT constraint_84 PRIMARY KEY (client_id, name);


--
-- Name: realm_attribute constraint_9; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_attribute
    ADD CONSTRAINT constraint_9 PRIMARY KEY (name, realm_id);


--
-- Name: realm_required_credential constraint_92; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_required_credential
    ADD CONSTRAINT constraint_92 PRIMARY KEY (realm_id, type);


--
-- Name: keycloak_role constraint_a; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT constraint_a PRIMARY KEY (id);


--
-- Name: admin_event_entity constraint_admin_event_entity; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.admin_event_entity
    ADD CONSTRAINT constraint_admin_event_entity PRIMARY KEY (id);


--
-- Name: authenticator_config_entry constraint_auth_cfg_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authenticator_config_entry
    ADD CONSTRAINT constraint_auth_cfg_pk PRIMARY KEY (authenticator_id, name);


--
-- Name: authentication_execution constraint_auth_exec_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT constraint_auth_exec_pk PRIMARY KEY (id);


--
-- Name: authentication_flow constraint_auth_flow_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authentication_flow
    ADD CONSTRAINT constraint_auth_flow_pk PRIMARY KEY (id);


--
-- Name: authenticator_config constraint_auth_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authenticator_config
    ADD CONSTRAINT constraint_auth_pk PRIMARY KEY (id);


--
-- Name: user_role_mapping constraint_c; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_role_mapping
    ADD CONSTRAINT constraint_c PRIMARY KEY (role_id, user_id);


--
-- Name: composite_role constraint_composite_role; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT constraint_composite_role PRIMARY KEY (composite, child_role);


--
-- Name: identity_provider_config constraint_d; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider_config
    ADD CONSTRAINT constraint_d PRIMARY KEY (identity_provider_id, name);


--
-- Name: policy_config constraint_dpc; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.policy_config
    ADD CONSTRAINT constraint_dpc PRIMARY KEY (policy_id, name);


--
-- Name: realm_smtp_config constraint_e; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_smtp_config
    ADD CONSTRAINT constraint_e PRIMARY KEY (realm_id, name);


--
-- Name: credential constraint_f; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.credential
    ADD CONSTRAINT constraint_f PRIMARY KEY (id);


--
-- Name: user_federation_config constraint_f9; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_config
    ADD CONSTRAINT constraint_f9 PRIMARY KEY (user_federation_provider_id, name);


--
-- Name: resource_server_perm_ticket constraint_fapmt; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT constraint_fapmt PRIMARY KEY (id);


--
-- Name: resource_server_resource constraint_farsr; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT constraint_farsr PRIMARY KEY (id);


--
-- Name: resource_server_policy constraint_farsrp; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT constraint_farsrp PRIMARY KEY (id);


--
-- Name: associated_policy constraint_farsrpap; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT constraint_farsrpap PRIMARY KEY (policy_id, associated_policy_id);


--
-- Name: resource_policy constraint_farsrpp; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT constraint_farsrpp PRIMARY KEY (resource_id, policy_id);


--
-- Name: resource_server_scope constraint_farsrs; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT constraint_farsrs PRIMARY KEY (id);


--
-- Name: resource_scope constraint_farsrsp; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT constraint_farsrsp PRIMARY KEY (resource_id, scope_id);


--
-- Name: scope_policy constraint_farsrsps; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT constraint_farsrsps PRIMARY KEY (scope_id, policy_id);


--
-- Name: user_entity constraint_fb; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT constraint_fb PRIMARY KEY (id);


--
-- Name: user_federation_mapper_config constraint_fedmapper_cfg_pm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_mapper_config
    ADD CONSTRAINT constraint_fedmapper_cfg_pm PRIMARY KEY (user_federation_mapper_id, name);


--
-- Name: user_federation_mapper constraint_fedmapperpm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT constraint_fedmapperpm PRIMARY KEY (id);


--
-- Name: fed_user_consent_cl_scope constraint_fgrntcsnt_clsc_pm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.fed_user_consent_cl_scope
    ADD CONSTRAINT constraint_fgrntcsnt_clsc_pm PRIMARY KEY (user_consent_id, scope_id);


--
-- Name: user_consent_client_scope constraint_grntcsnt_clsc_pm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent_client_scope
    ADD CONSTRAINT constraint_grntcsnt_clsc_pm PRIMARY KEY (user_consent_id, scope_id);


--
-- Name: user_consent constraint_grntcsnt_pm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT constraint_grntcsnt_pm PRIMARY KEY (id);


--
-- Name: keycloak_group constraint_group; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.keycloak_group
    ADD CONSTRAINT constraint_group PRIMARY KEY (id);


--
-- Name: group_attribute constraint_group_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.group_attribute
    ADD CONSTRAINT constraint_group_attribute_pk PRIMARY KEY (id);


--
-- Name: group_role_mapping constraint_group_role; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.group_role_mapping
    ADD CONSTRAINT constraint_group_role PRIMARY KEY (role_id, group_id);


--
-- Name: identity_provider_mapper constraint_idpm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider_mapper
    ADD CONSTRAINT constraint_idpm PRIMARY KEY (id);


--
-- Name: idp_mapper_config constraint_idpmconfig; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.idp_mapper_config
    ADD CONSTRAINT constraint_idpmconfig PRIMARY KEY (idp_mapper_id, name);


--
-- Name: migration_model constraint_migmod; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.migration_model
    ADD CONSTRAINT constraint_migmod PRIMARY KEY (id);


--
-- Name: offline_client_session constraint_offl_cl_ses_pk3; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.offline_client_session
    ADD CONSTRAINT constraint_offl_cl_ses_pk3 PRIMARY KEY (user_session_id, client_id, client_storage_provider, external_client_id, offline_flag);


--
-- Name: offline_user_session constraint_offl_us_ses_pk2; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.offline_user_session
    ADD CONSTRAINT constraint_offl_us_ses_pk2 PRIMARY KEY (user_session_id, offline_flag);


--
-- Name: protocol_mapper constraint_pcm; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT constraint_pcm PRIMARY KEY (id);


--
-- Name: protocol_mapper_config constraint_pmconfig; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.protocol_mapper_config
    ADD CONSTRAINT constraint_pmconfig PRIMARY KEY (protocol_mapper_id, name);


--
-- Name: redirect_uris constraint_redirect_uris; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.redirect_uris
    ADD CONSTRAINT constraint_redirect_uris PRIMARY KEY (client_id, value);


--
-- Name: required_action_config constraint_req_act_cfg_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.required_action_config
    ADD CONSTRAINT constraint_req_act_cfg_pk PRIMARY KEY (required_action_id, name);


--
-- Name: required_action_provider constraint_req_act_prv_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.required_action_provider
    ADD CONSTRAINT constraint_req_act_prv_pk PRIMARY KEY (id);


--
-- Name: user_required_action constraint_required_action; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_required_action
    ADD CONSTRAINT constraint_required_action PRIMARY KEY (required_action, user_id);


--
-- Name: resource_uris constraint_resour_uris_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_uris
    ADD CONSTRAINT constraint_resour_uris_pk PRIMARY KEY (resource_id, value);


--
-- Name: role_attribute constraint_role_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.role_attribute
    ADD CONSTRAINT constraint_role_attribute_pk PRIMARY KEY (id);


--
-- Name: revoked_token constraint_rt; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.revoked_token
    ADD CONSTRAINT constraint_rt PRIMARY KEY (id);


--
-- Name: user_attribute constraint_user_attribute_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_attribute
    ADD CONSTRAINT constraint_user_attribute_pk PRIMARY KEY (id);


--
-- Name: user_group_membership constraint_user_group; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_group_membership
    ADD CONSTRAINT constraint_user_group PRIMARY KEY (group_id, user_id);


--
-- Name: web_origins constraint_web_origins; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.web_origins
    ADD CONSTRAINT constraint_web_origins PRIMARY KEY (client_id, value);


--
-- Name: databasechangeloglock databasechangeloglock_pkey; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.databasechangeloglock
    ADD CONSTRAINT databasechangeloglock_pkey PRIMARY KEY (id);


--
-- Name: client_scope_attributes pk_cl_tmpl_attr; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope_attributes
    ADD CONSTRAINT pk_cl_tmpl_attr PRIMARY KEY (scope_id, name);


--
-- Name: client_scope pk_cli_template; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope
    ADD CONSTRAINT pk_cli_template PRIMARY KEY (id);


--
-- Name: resource_server pk_resource_server; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server
    ADD CONSTRAINT pk_resource_server PRIMARY KEY (id);


--
-- Name: client_scope_role_mapping pk_template_scope; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope_role_mapping
    ADD CONSTRAINT pk_template_scope PRIMARY KEY (scope_id, role_id);


--
-- Name: default_client_scope r_def_cli_scope_bind; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.default_client_scope
    ADD CONSTRAINT r_def_cli_scope_bind PRIMARY KEY (realm_id, scope_id);


--
-- Name: realm_localizations realm_localizations_pkey; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_localizations
    ADD CONSTRAINT realm_localizations_pkey PRIMARY KEY (realm_id, locale);


--
-- Name: resource_attribute res_attr_pk; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_attribute
    ADD CONSTRAINT res_attr_pk PRIMARY KEY (id);


--
-- Name: keycloak_group sibling_names; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.keycloak_group
    ADD CONSTRAINT sibling_names UNIQUE (realm_id, parent_group, name);


--
-- Name: identity_provider uk_2daelwnibji49avxsrtuf6xj33; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT uk_2daelwnibji49avxsrtuf6xj33 UNIQUE (provider_alias, realm_id);


--
-- Name: client uk_b71cjlbenv945rb6gcon438at; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client
    ADD CONSTRAINT uk_b71cjlbenv945rb6gcon438at UNIQUE (realm_id, client_id);


--
-- Name: client_scope uk_cli_scope; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope
    ADD CONSTRAINT uk_cli_scope UNIQUE (realm_id, name);


--
-- Name: user_entity uk_dykn684sl8up1crfei6eckhd7; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT uk_dykn684sl8up1crfei6eckhd7 UNIQUE (realm_id, email_constraint);


--
-- Name: user_consent uk_external_consent; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT uk_external_consent UNIQUE (client_storage_provider, external_client_id, user_id);


--
-- Name: resource_server_resource uk_frsr6t700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT uk_frsr6t700s9v50bu18ws5ha6 UNIQUE (name, owner, resource_server_id);


--
-- Name: resource_server_perm_ticket uk_frsr6t700s9v50bu18ws5pmt; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT uk_frsr6t700s9v50bu18ws5pmt UNIQUE (owner, requester, resource_server_id, resource_id, scope_id);


--
-- Name: resource_server_policy uk_frsrpt700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT uk_frsrpt700s9v50bu18ws5ha6 UNIQUE (name, resource_server_id);


--
-- Name: resource_server_scope uk_frsrst700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT uk_frsrst700s9v50bu18ws5ha6 UNIQUE (name, resource_server_id);


--
-- Name: user_consent uk_local_consent; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT uk_local_consent UNIQUE (client_id, user_id);


--
-- Name: org uk_org_alias; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_alias UNIQUE (realm_id, alias);


--
-- Name: org uk_org_group; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_group UNIQUE (group_id);


--
-- Name: org uk_org_name; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.org
    ADD CONSTRAINT uk_org_name UNIQUE (realm_id, name);


--
-- Name: realm uk_orvsdmla56612eaefiq6wl5oi; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm
    ADD CONSTRAINT uk_orvsdmla56612eaefiq6wl5oi UNIQUE (name);


--
-- Name: user_entity uk_ru8tt6t700s9v50bu18ws5ha6; Type: CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_entity
    ADD CONSTRAINT uk_ru8tt6t700s9v50bu18ws5ha6 UNIQUE (realm_id, username);


--
-- Name: fed_user_attr_long_values; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX fed_user_attr_long_values ON public.fed_user_attribute USING btree (long_value_hash, name);


--
-- Name: fed_user_attr_long_values_lower_case; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX fed_user_attr_long_values_lower_case ON public.fed_user_attribute USING btree (long_value_hash_lower_case, name);


--
-- Name: idx_admin_event_time; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_admin_event_time ON public.admin_event_entity USING btree (realm_id, admin_event_time);


--
-- Name: idx_assoc_pol_assoc_pol_id; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_assoc_pol_assoc_pol_id ON public.associated_policy USING btree (associated_policy_id);


--
-- Name: idx_auth_config_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_auth_config_realm ON public.authenticator_config USING btree (realm_id);


--
-- Name: idx_auth_exec_flow; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_auth_exec_flow ON public.authentication_execution USING btree (flow_id);


--
-- Name: idx_auth_exec_realm_flow; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_auth_exec_realm_flow ON public.authentication_execution USING btree (realm_id, flow_id);


--
-- Name: idx_auth_flow_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_auth_flow_realm ON public.authentication_flow USING btree (realm_id);


--
-- Name: idx_cl_clscope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_cl_clscope ON public.client_scope_client USING btree (scope_id);


--
-- Name: idx_client_att_by_name_value; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_client_att_by_name_value ON public.client_attributes USING btree (name, substr(value, 1, 255));


--
-- Name: idx_client_id; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_client_id ON public.client USING btree (client_id);


--
-- Name: idx_client_init_acc_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_client_init_acc_realm ON public.client_initial_access USING btree (realm_id);


--
-- Name: idx_clscope_attrs; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_clscope_attrs ON public.client_scope_attributes USING btree (scope_id);


--
-- Name: idx_clscope_cl; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_clscope_cl ON public.client_scope_client USING btree (client_id);


--
-- Name: idx_clscope_protmap; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_clscope_protmap ON public.protocol_mapper USING btree (client_scope_id);


--
-- Name: idx_clscope_role; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_clscope_role ON public.client_scope_role_mapping USING btree (scope_id);


--
-- Name: idx_compo_config_compo; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_compo_config_compo ON public.component_config USING btree (component_id);


--
-- Name: idx_component_provider_type; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_component_provider_type ON public.component USING btree (provider_type);


--
-- Name: idx_component_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_component_realm ON public.component USING btree (realm_id);


--
-- Name: idx_composite; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_composite ON public.composite_role USING btree (composite);


--
-- Name: idx_composite_child; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_composite_child ON public.composite_role USING btree (child_role);


--
-- Name: idx_defcls_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_defcls_realm ON public.default_client_scope USING btree (realm_id);


--
-- Name: idx_defcls_scope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_defcls_scope ON public.default_client_scope USING btree (scope_id);


--
-- Name: idx_event_time; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_event_time ON public.event_entity USING btree (realm_id, event_time);


--
-- Name: idx_fedidentity_feduser; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fedidentity_feduser ON public.federated_identity USING btree (federated_user_id);


--
-- Name: idx_fedidentity_user; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fedidentity_user ON public.federated_identity USING btree (user_id);


--
-- Name: idx_fu_attribute; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_attribute ON public.fed_user_attribute USING btree (user_id, realm_id, name);


--
-- Name: idx_fu_cnsnt_ext; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_cnsnt_ext ON public.fed_user_consent USING btree (user_id, client_storage_provider, external_client_id);


--
-- Name: idx_fu_consent; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_consent ON public.fed_user_consent USING btree (user_id, client_id);


--
-- Name: idx_fu_consent_ru; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_consent_ru ON public.fed_user_consent USING btree (realm_id, user_id);


--
-- Name: idx_fu_credential; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_credential ON public.fed_user_credential USING btree (user_id, type);


--
-- Name: idx_fu_credential_ru; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_credential_ru ON public.fed_user_credential USING btree (realm_id, user_id);


--
-- Name: idx_fu_group_membership; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_group_membership ON public.fed_user_group_membership USING btree (user_id, group_id);


--
-- Name: idx_fu_group_membership_ru; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_group_membership_ru ON public.fed_user_group_membership USING btree (realm_id, user_id);


--
-- Name: idx_fu_required_action; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_required_action ON public.fed_user_required_action USING btree (user_id, required_action);


--
-- Name: idx_fu_required_action_ru; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_required_action_ru ON public.fed_user_required_action USING btree (realm_id, user_id);


--
-- Name: idx_fu_role_mapping; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_role_mapping ON public.fed_user_role_mapping USING btree (user_id, role_id);


--
-- Name: idx_fu_role_mapping_ru; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_fu_role_mapping_ru ON public.fed_user_role_mapping USING btree (realm_id, user_id);


--
-- Name: idx_group_att_by_name_value; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_group_att_by_name_value ON public.group_attribute USING btree (name, ((value)::character varying(250)));


--
-- Name: idx_group_attr_group; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_group_attr_group ON public.group_attribute USING btree (group_id);


--
-- Name: idx_group_role_mapp_group; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_group_role_mapp_group ON public.group_role_mapping USING btree (group_id);


--
-- Name: idx_id_prov_mapp_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_id_prov_mapp_realm ON public.identity_provider_mapper USING btree (realm_id);


--
-- Name: idx_ident_prov_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_ident_prov_realm ON public.identity_provider USING btree (realm_id);


--
-- Name: idx_idp_for_login; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_idp_for_login ON public.identity_provider USING btree (realm_id, enabled, link_only, hide_on_login, organization_id);


--
-- Name: idx_idp_realm_org; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_idp_realm_org ON public.identity_provider USING btree (realm_id, organization_id);


--
-- Name: idx_keycloak_role_client; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_keycloak_role_client ON public.keycloak_role USING btree (client);


--
-- Name: idx_keycloak_role_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_keycloak_role_realm ON public.keycloak_role USING btree (realm);


--
-- Name: idx_offline_uss_by_broker_session_id; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_offline_uss_by_broker_session_id ON public.offline_user_session USING btree (broker_session_id, realm_id);


--
-- Name: idx_offline_uss_by_last_session_refresh; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_offline_uss_by_last_session_refresh ON public.offline_user_session USING btree (realm_id, offline_flag, last_session_refresh);


--
-- Name: idx_offline_uss_by_user; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_offline_uss_by_user ON public.offline_user_session USING btree (user_id, realm_id, offline_flag);


--
-- Name: idx_org_domain_org_id; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_org_domain_org_id ON public.org_domain USING btree (org_id);


--
-- Name: idx_perm_ticket_owner; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_perm_ticket_owner ON public.resource_server_perm_ticket USING btree (owner);


--
-- Name: idx_perm_ticket_requester; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_perm_ticket_requester ON public.resource_server_perm_ticket USING btree (requester);


--
-- Name: idx_protocol_mapper_client; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_protocol_mapper_client ON public.protocol_mapper USING btree (client_id);


--
-- Name: idx_realm_attr_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_attr_realm ON public.realm_attribute USING btree (realm_id);


--
-- Name: idx_realm_clscope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_clscope ON public.client_scope USING btree (realm_id);


--
-- Name: idx_realm_def_grp_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_def_grp_realm ON public.realm_default_groups USING btree (realm_id);


--
-- Name: idx_realm_evt_list_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_evt_list_realm ON public.realm_events_listeners USING btree (realm_id);


--
-- Name: idx_realm_evt_types_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_evt_types_realm ON public.realm_enabled_event_types USING btree (realm_id);


--
-- Name: idx_realm_master_adm_cli; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_master_adm_cli ON public.realm USING btree (master_admin_client);


--
-- Name: idx_realm_supp_local_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_realm_supp_local_realm ON public.realm_supported_locales USING btree (realm_id);


--
-- Name: idx_redir_uri_client; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_redir_uri_client ON public.redirect_uris USING btree (client_id);


--
-- Name: idx_req_act_prov_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_req_act_prov_realm ON public.required_action_provider USING btree (realm_id);


--
-- Name: idx_res_policy_policy; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_res_policy_policy ON public.resource_policy USING btree (policy_id);


--
-- Name: idx_res_scope_scope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_res_scope_scope ON public.resource_scope USING btree (scope_id);


--
-- Name: idx_res_serv_pol_res_serv; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_res_serv_pol_res_serv ON public.resource_server_policy USING btree (resource_server_id);


--
-- Name: idx_res_srv_res_res_srv; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_res_srv_res_res_srv ON public.resource_server_resource USING btree (resource_server_id);


--
-- Name: idx_res_srv_scope_res_srv; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_res_srv_scope_res_srv ON public.resource_server_scope USING btree (resource_server_id);


--
-- Name: idx_rev_token_on_expire; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_rev_token_on_expire ON public.revoked_token USING btree (expire);


--
-- Name: idx_role_attribute; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_role_attribute ON public.role_attribute USING btree (role_id);


--
-- Name: idx_role_clscope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_role_clscope ON public.client_scope_role_mapping USING btree (role_id);


--
-- Name: idx_scope_mapping_role; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_scope_mapping_role ON public.scope_mapping USING btree (role_id);


--
-- Name: idx_scope_policy_policy; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_scope_policy_policy ON public.scope_policy USING btree (policy_id);


--
-- Name: idx_update_time; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_update_time ON public.migration_model USING btree (update_time);


--
-- Name: idx_usconsent_clscope; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_usconsent_clscope ON public.user_consent_client_scope USING btree (user_consent_id);


--
-- Name: idx_usconsent_scope_id; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_usconsent_scope_id ON public.user_consent_client_scope USING btree (scope_id);


--
-- Name: idx_user_attribute; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_attribute ON public.user_attribute USING btree (user_id);


--
-- Name: idx_user_attribute_name; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_attribute_name ON public.user_attribute USING btree (name, value);


--
-- Name: idx_user_consent; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_consent ON public.user_consent USING btree (user_id);


--
-- Name: idx_user_credential; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_credential ON public.credential USING btree (user_id);


--
-- Name: idx_user_email; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_email ON public.user_entity USING btree (email);


--
-- Name: idx_user_group_mapping; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_group_mapping ON public.user_group_membership USING btree (user_id);


--
-- Name: idx_user_reqactions; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_reqactions ON public.user_required_action USING btree (user_id);


--
-- Name: idx_user_role_mapping; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_role_mapping ON public.user_role_mapping USING btree (user_id);


--
-- Name: idx_user_service_account; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_user_service_account ON public.user_entity USING btree (realm_id, service_account_client_link);


--
-- Name: idx_usr_fed_map_fed_prv; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_usr_fed_map_fed_prv ON public.user_federation_mapper USING btree (federation_provider_id);


--
-- Name: idx_usr_fed_map_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_usr_fed_map_realm ON public.user_federation_mapper USING btree (realm_id);


--
-- Name: idx_usr_fed_prv_realm; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_usr_fed_prv_realm ON public.user_federation_provider USING btree (realm_id);


--
-- Name: idx_web_orig_client; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX idx_web_orig_client ON public.web_origins USING btree (client_id);


--
-- Name: user_attr_long_values; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX user_attr_long_values ON public.user_attribute USING btree (long_value_hash, name);


--
-- Name: user_attr_long_values_lower_case; Type: INDEX; Schema: public; Owner: keycloak
--

CREATE INDEX user_attr_long_values_lower_case ON public.user_attribute USING btree (long_value_hash_lower_case, name);


--
-- Name: identity_provider fk2b4ebc52ae5c3b34; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider
    ADD CONSTRAINT fk2b4ebc52ae5c3b34 FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: client_attributes fk3c47c64beacca966; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_attributes
    ADD CONSTRAINT fk3c47c64beacca966 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: federated_identity fk404288b92ef007a6; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.federated_identity
    ADD CONSTRAINT fk404288b92ef007a6 FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: client_node_registrations fk4129723ba992f594; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_node_registrations
    ADD CONSTRAINT fk4129723ba992f594 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: redirect_uris fk_1burs8pb4ouj97h5wuppahv9f; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.redirect_uris
    ADD CONSTRAINT fk_1burs8pb4ouj97h5wuppahv9f FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: user_federation_provider fk_1fj32f6ptolw2qy60cd8n01e8; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_provider
    ADD CONSTRAINT fk_1fj32f6ptolw2qy60cd8n01e8 FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_required_credential fk_5hg65lybevavkqfki3kponh9v; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_required_credential
    ADD CONSTRAINT fk_5hg65lybevavkqfki3kponh9v FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: resource_attribute fk_5hrm2vlf9ql5fu022kqepovbr; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_attribute
    ADD CONSTRAINT fk_5hrm2vlf9ql5fu022kqepovbr FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: user_attribute fk_5hrm2vlf9ql5fu043kqepovbr; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_attribute
    ADD CONSTRAINT fk_5hrm2vlf9ql5fu043kqepovbr FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: user_required_action fk_6qj3w1jw9cvafhe19bwsiuvmd; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_required_action
    ADD CONSTRAINT fk_6qj3w1jw9cvafhe19bwsiuvmd FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: keycloak_role fk_6vyqfe4cn4wlq8r6kt5vdsj5c; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.keycloak_role
    ADD CONSTRAINT fk_6vyqfe4cn4wlq8r6kt5vdsj5c FOREIGN KEY (realm) REFERENCES public.realm(id);


--
-- Name: realm_smtp_config fk_70ej8xdxgxd0b9hh6180irr0o; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_smtp_config
    ADD CONSTRAINT fk_70ej8xdxgxd0b9hh6180irr0o FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_attribute fk_8shxd6l3e9atqukacxgpffptw; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_attribute
    ADD CONSTRAINT fk_8shxd6l3e9atqukacxgpffptw FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: composite_role fk_a63wvekftu8jo1pnj81e7mce2; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT fk_a63wvekftu8jo1pnj81e7mce2 FOREIGN KEY (composite) REFERENCES public.keycloak_role(id);


--
-- Name: authentication_execution fk_auth_exec_flow; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT fk_auth_exec_flow FOREIGN KEY (flow_id) REFERENCES public.authentication_flow(id);


--
-- Name: authentication_execution fk_auth_exec_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authentication_execution
    ADD CONSTRAINT fk_auth_exec_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: authentication_flow fk_auth_flow_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authentication_flow
    ADD CONSTRAINT fk_auth_flow_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: authenticator_config fk_auth_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.authenticator_config
    ADD CONSTRAINT fk_auth_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_role_mapping fk_c4fqv34p1mbylloxang7b1q3l; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_role_mapping
    ADD CONSTRAINT fk_c4fqv34p1mbylloxang7b1q3l FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: client_scope_attributes fk_cl_scope_attr_scope; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope_attributes
    ADD CONSTRAINT fk_cl_scope_attr_scope FOREIGN KEY (scope_id) REFERENCES public.client_scope(id);


--
-- Name: client_scope_role_mapping fk_cl_scope_rm_scope; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_scope_role_mapping
    ADD CONSTRAINT fk_cl_scope_rm_scope FOREIGN KEY (scope_id) REFERENCES public.client_scope(id);


--
-- Name: protocol_mapper fk_cli_scope_mapper; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT fk_cli_scope_mapper FOREIGN KEY (client_scope_id) REFERENCES public.client_scope(id);


--
-- Name: client_initial_access fk_client_init_acc_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.client_initial_access
    ADD CONSTRAINT fk_client_init_acc_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: component_config fk_component_config; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.component_config
    ADD CONSTRAINT fk_component_config FOREIGN KEY (component_id) REFERENCES public.component(id);


--
-- Name: component fk_component_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.component
    ADD CONSTRAINT fk_component_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_default_groups fk_def_groups_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_default_groups
    ADD CONSTRAINT fk_def_groups_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_federation_mapper_config fk_fedmapper_cfg; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_mapper_config
    ADD CONSTRAINT fk_fedmapper_cfg FOREIGN KEY (user_federation_mapper_id) REFERENCES public.user_federation_mapper(id);


--
-- Name: user_federation_mapper fk_fedmapperpm_fedprv; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT fk_fedmapperpm_fedprv FOREIGN KEY (federation_provider_id) REFERENCES public.user_federation_provider(id);


--
-- Name: user_federation_mapper fk_fedmapperpm_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_mapper
    ADD CONSTRAINT fk_fedmapperpm_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: associated_policy fk_frsr5s213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT fk_frsr5s213xcx4wnkog82ssrfy FOREIGN KEY (associated_policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: scope_policy fk_frsrasp13xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT fk_frsrasp13xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog82sspmt; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog82sspmt FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_server_resource fk_frsrho213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_resource
    ADD CONSTRAINT fk_frsrho213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog83sspmt; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog83sspmt FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_server_perm_ticket fk_frsrho213xcx4wnkog84sspmt; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrho213xcx4wnkog84sspmt FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: associated_policy fk_frsrpas14xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.associated_policy
    ADD CONSTRAINT fk_frsrpas14xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: scope_policy fk_frsrpass3xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.scope_policy
    ADD CONSTRAINT fk_frsrpass3xcx4wnkog82ssrfy FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: resource_server_perm_ticket fk_frsrpo2128cx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_perm_ticket
    ADD CONSTRAINT fk_frsrpo2128cx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_server_policy fk_frsrpo213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_policy
    ADD CONSTRAINT fk_frsrpo213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: resource_scope fk_frsrpos13xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT fk_frsrpos13xcx4wnkog82ssrfy FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_policy fk_frsrpos53xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT fk_frsrpos53xcx4wnkog82ssrfy FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: resource_policy fk_frsrpp213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_policy
    ADD CONSTRAINT fk_frsrpp213xcx4wnkog82ssrfy FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: resource_scope fk_frsrps213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_scope
    ADD CONSTRAINT fk_frsrps213xcx4wnkog82ssrfy FOREIGN KEY (scope_id) REFERENCES public.resource_server_scope(id);


--
-- Name: resource_server_scope fk_frsrso213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_server_scope
    ADD CONSTRAINT fk_frsrso213xcx4wnkog82ssrfy FOREIGN KEY (resource_server_id) REFERENCES public.resource_server(id);


--
-- Name: composite_role fk_gr7thllb9lu8q4vqa4524jjy8; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.composite_role
    ADD CONSTRAINT fk_gr7thllb9lu8q4vqa4524jjy8 FOREIGN KEY (child_role) REFERENCES public.keycloak_role(id);


--
-- Name: user_consent_client_scope fk_grntcsnt_clsc_usc; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent_client_scope
    ADD CONSTRAINT fk_grntcsnt_clsc_usc FOREIGN KEY (user_consent_id) REFERENCES public.user_consent(id);


--
-- Name: user_consent fk_grntcsnt_user; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_consent
    ADD CONSTRAINT fk_grntcsnt_user FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: group_attribute fk_group_attribute_group; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.group_attribute
    ADD CONSTRAINT fk_group_attribute_group FOREIGN KEY (group_id) REFERENCES public.keycloak_group(id);


--
-- Name: group_role_mapping fk_group_role_group; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.group_role_mapping
    ADD CONSTRAINT fk_group_role_group FOREIGN KEY (group_id) REFERENCES public.keycloak_group(id);


--
-- Name: realm_enabled_event_types fk_h846o4h0w8epx5nwedrf5y69j; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_enabled_event_types
    ADD CONSTRAINT fk_h846o4h0w8epx5nwedrf5y69j FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: realm_events_listeners fk_h846o4h0w8epx5nxev9f5y69j; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_events_listeners
    ADD CONSTRAINT fk_h846o4h0w8epx5nxev9f5y69j FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: identity_provider_mapper fk_idpm_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider_mapper
    ADD CONSTRAINT fk_idpm_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: idp_mapper_config fk_idpmconfig; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.idp_mapper_config
    ADD CONSTRAINT fk_idpmconfig FOREIGN KEY (idp_mapper_id) REFERENCES public.identity_provider_mapper(id);


--
-- Name: web_origins fk_lojpho213xcx4wnkog82ssrfy; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.web_origins
    ADD CONSTRAINT fk_lojpho213xcx4wnkog82ssrfy FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: scope_mapping fk_ouse064plmlr732lxjcn1q5f1; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.scope_mapping
    ADD CONSTRAINT fk_ouse064plmlr732lxjcn1q5f1 FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: protocol_mapper fk_pcm_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.protocol_mapper
    ADD CONSTRAINT fk_pcm_realm FOREIGN KEY (client_id) REFERENCES public.client(id);


--
-- Name: credential fk_pfyr0glasqyl0dei3kl69r6v0; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.credential
    ADD CONSTRAINT fk_pfyr0glasqyl0dei3kl69r6v0 FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: protocol_mapper_config fk_pmconfig; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.protocol_mapper_config
    ADD CONSTRAINT fk_pmconfig FOREIGN KEY (protocol_mapper_id) REFERENCES public.protocol_mapper(id);


--
-- Name: default_client_scope fk_r_def_cli_scope_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.default_client_scope
    ADD CONSTRAINT fk_r_def_cli_scope_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: required_action_provider fk_req_act_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.required_action_provider
    ADD CONSTRAINT fk_req_act_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: resource_uris fk_resource_server_uris; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.resource_uris
    ADD CONSTRAINT fk_resource_server_uris FOREIGN KEY (resource_id) REFERENCES public.resource_server_resource(id);


--
-- Name: role_attribute fk_role_attribute_id; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.role_attribute
    ADD CONSTRAINT fk_role_attribute_id FOREIGN KEY (role_id) REFERENCES public.keycloak_role(id);


--
-- Name: realm_supported_locales fk_supported_locales_realm; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.realm_supported_locales
    ADD CONSTRAINT fk_supported_locales_realm FOREIGN KEY (realm_id) REFERENCES public.realm(id);


--
-- Name: user_federation_config fk_t13hpu1j94r2ebpekr39x5eu5; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_federation_config
    ADD CONSTRAINT fk_t13hpu1j94r2ebpekr39x5eu5 FOREIGN KEY (user_federation_provider_id) REFERENCES public.user_federation_provider(id);


--
-- Name: user_group_membership fk_user_group_user; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.user_group_membership
    ADD CONSTRAINT fk_user_group_user FOREIGN KEY (user_id) REFERENCES public.user_entity(id);


--
-- Name: policy_config fkdc34197cf864c4e43; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.policy_config
    ADD CONSTRAINT fkdc34197cf864c4e43 FOREIGN KEY (policy_id) REFERENCES public.resource_server_policy(id);


--
-- Name: identity_provider_config fkdc4897cf864c4e43; Type: FK CONSTRAINT; Schema: public; Owner: keycloak
--

ALTER TABLE ONLY public.identity_provider_config
    ADD CONSTRAINT fkdc4897cf864c4e43 FOREIGN KEY (identity_provider_id) REFERENCES public.identity_provider(internal_id);


--
-- PostgreSQL database dump complete
--

\unrestrict 1YGfZC1sntbjlr09pJvww3m7U5KnopS9LeR9BNTZNTFxQimgHcRfPX2Hkw8ZpgQ

