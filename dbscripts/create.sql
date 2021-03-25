create sequence sq_user_removals;

create table user_removals (
    id bigint default nextval('sq_user_removals') primary key,
    completed timestamp,
    created timestamp default current_timestamp,
    remove_on date,
    started timestamp,
    status varchar(255),
    ldap_username varchar(255),
    github_username varchar(255)
);

create sequence sq_repository_forks;

create table repository_forks (
    id bigint default nextval('sq_repository_forks') primary key,
    user_removal_id bigint not null,
    created timestamp,
    deleted timestamp,
    repository_name varchar(255),
    repository_url varchar(255),
    source_repository_name varchar(255),
    source_repository_url varchar(255),
    status varchar(255)
);

alter table repository_forks add constraint fk_repository_forks_user_removal_id foreign key (user_removal_id) references user_removals;

create sequence sq_github_orgs;

create table github_orgs (
    id bigint default nextval('sq_github_orgs') primary key,
    name varchar(255) unique,
    unsubscribe_users_from_org boolean default false,
    subscriptions_enabled boolean default true
);

create sequence sq_github_teams;

create table github_teams (
    id bigint default nextval('sq_github_teams') primary key,
    org_id bigint not null,
    name varchar(255),
    github_id bigint unique,
    constraint fk_github_teams_org_id foreign key (org_id) references github_orgs (id)
);

create sequence sq_users;

create table users (
    id bigint default nextval('sq_users') primary key,
    krb_name varchar(255) unique,
    github_name varchar(255) unique,
    note varchar(255),
    admin boolean not null default false,
    whitelisted boolean not null default false,
    responsible_person varchar(255),
    created timestamp default CURRENT_TIMESTAMP
);

create table application_parameters (
    param_name varchar(255) primary key,
    param_value varchar(255)
);

create sequence sq_removal_logs;

create table removal_logs (
    id bigint default nextval('sq_removal_logs') primary key,
    user_removal_id bigint,
    created timestamp default CURRENT_TIMESTAMP,
    message varchar(255),
    stack_trace text
);

create sequence sq_unsubscribed_users_from_teams;

create table unsubscribed_users_from_teams (
    id bigint default nextval('sq_unsubscribed_users_from_teams') primary key,
    user_removal_id bigint not null,
    github_username varchar(255),
    github_team_name varchar(255),
    github_org_name varchar(255),
    status varchar(255),
    created timestamp default CURRENT_TIMESTAMP,
    constraint fk_unsubscribed_users_from_teams foreign key (user_removal_id) references user_removals (id)
);

create sequence sq_unsubscribed_users_from_orgs;

create table unsubscribed_users_from_orgs (
    id bigint default nextval('sq_unsubscribed_users_from_orgs') primary key,
    user_removal_id bigint not null,
    github_username varchar(255),
    github_org_name varchar(255),
    status varchar(255),
    created timestamp default CURRENT_TIMESTAMP,
    constraint fk_unsubscribed_users_from_orgs foreign key (user_removal_id) references user_removals (id)
);