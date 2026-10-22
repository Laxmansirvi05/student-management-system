-- A notice can carry the college's official document: the scanned circular with its stamp and
-- signature, as a PDF or a JPEG in the private bucket. It is shown only through a short-lived
-- signed link, under the same rules as the notice itself. The three columns are set together.
alter table app.notices
    add column document_path varchar(300),
    add column document_type varchar(30),
    add column document_size integer,
    add constraint notices_document_path_key unique (document_path),
    add constraint notices_document_type_check
        check (document_type is null or document_type in ('application/pdf', 'image/jpeg')),
    add constraint notices_document_size_check check (document_size is null or document_size > 0),
    add constraint notices_document_complete_check
        check ((document_path is null) = (document_type is null)
               and (document_path is null) = (document_size is null));
