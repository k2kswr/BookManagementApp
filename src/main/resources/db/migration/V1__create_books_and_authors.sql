CREATE TABLE authors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    birth_date DATE NOT NULL
);

CREATE TABLE books (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title TEXT NOT NULL CHECK (length(trim(title)) > 0),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    publication_status VARCHAR(11) NOT NULL
        CHECK (publication_status IN ('UNPUBLISHED', 'PUBLISHED'))
);

CREATE TABLE book_authors (
    book_id BIGINT NOT NULL REFERENCES books(id),
    author_id BIGINT NOT NULL REFERENCES authors(id),
    PRIMARY KEY (book_id, author_id)
);

CREATE INDEX book_authors_author_id_book_id_idx ON book_authors(author_id, book_id);
