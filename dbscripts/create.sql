create sequence sq_user_removals;

create table user_removals (
    id bigint not null,
    completed timestamp,
    created timestamp,
    remove_on date,
    started timestamp,
    status varchar(255),
    username varchar(255),
    primary key (id)
);

create sequence sq_repository_forks;

create table repository_forks (
    id bigint not null,
    user_removal_id bigint not null,
    archived timestamp,
    created timestamp,
    repository_name varchar(255),
    repository_url varchar(255),
    source_repository_name varchar(255),
    source_repository_url varchar(255),
    primary key (id)
);

alter table repository_forks add constraint fk_repository_forks_user_removal_id foreign key (user_removal_id) references user_removals;

create sequence sq_github_orgs;

create table github_orgs (
    id bigint default nextval('sq_github_orgs') primary key,
    name varchar(255) unique
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
    whitelisted boolean not null default false
);

create table application_parameters (
    param_name varchar(255) primary key,
    param_value varchar(255)
);

create sequence sq_removal_logs;

create table removal_logs (
    id bigint default nextval('sq_removal_logs') primary key,
    user_removal_id bigint not null,
    message varchar(255),
    stack_trace text
);
