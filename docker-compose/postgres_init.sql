create user data_index password 'data_index';
create schema data_index;
alter schema data_index owner to data_index;
alter user data_index set SEARCH_PATH=data_index;
