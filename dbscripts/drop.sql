drop sequence if exists sq_repository_forks;
drop sequence if exists sq_user_removals;
drop sequence if exists sq_github_orgs;
drop sequence if exists sq_github_teams;
drop sequence if exists sq_users;
drop sequence if exists sq_removal_logs;
drop sequence if exists sq_unsubscribed_users_from_teams;
drop sequence if exists sq_unsubscribed_users_from_orgs;

alter table if exists github_teams drop constraint if exists FK_GITHUB_TEAMS_ORG_ID;
alter table if exists unsubscribed_users_from_orgs drop constraint if exists fk_unsubscribed_users_from_orgs;
alter table if exists unsubscribed_users_from_teams drop constraint if exists fk_unsubscribed_users_from_teams;

drop table if exists repository_forks;
drop table if exists user_removals;
drop table if exists github_orgs;
drop table if exists github_teams;
drop table if exists users;
drop table if exists application_parameters;
drop table if exists removal_logs;
drop table if exists unsubscribed_users_from_teams;
drop table if exists unsubscribed_users_from_orgs;
