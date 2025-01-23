alter table github_teams add column selfservice boolean;
update github_teams set selfservice = true;
