alter table github_orgs add column unsubscribe_users_from_org boolean default false;
alter table github_orgs add column subscriptions_enabled boolean default true;

update github_orgs set unsubscribe_users_from_org = false;
update github_orgs set subscriptions_enabled = true;
