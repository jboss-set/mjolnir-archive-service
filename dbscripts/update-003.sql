alter table github_orgs add column unsubscribe_users_from_org boolean default false;

update github_orgs set unsubscribe_users_from_org = false;
