const state = { authors: [], books: [] };

const byId = (id) => document.getElementById(id);
const escapeHtml = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#039;");

async function request(path, options = {}) {
  const response = await fetch(path, options);
  if (response.ok) return response.status === 204 ? null : response.json();
  let problem = {};
  try { problem = await response.json(); } catch { /* JSONでないエラーは一般メッセージを使う */ }
  const fields = Array.isArray(problem.errors)
    ? `（${problem.errors.map((error) => `${error.field}: ${error.message}`).join("、")}）`
    : "";
  throw new Error(`${problem.detail || "処理に失敗しました。"}${fields}`);
}

function showNotice(message, isError = false) {
  const notice = byId("notice");
  notice.textContent = message;
  notice.classList.toggle("is-error", isError);
  notice.hidden = false;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function clearNotice() { byId("notice").hidden = true; }

function price(value) {
  return Number(value).toLocaleString("ja-JP", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

function renderAuthors() {
  byId("author-count").textContent = state.authors.length;
  byId("authors-empty").hidden = state.authors.length !== 0;
  byId("authors-table").innerHTML = state.authors.map((author) => `
    <tr>
      <td>${author.id}</td><td>${escapeHtml(author.name)}</td><td>${author.birthDate}</td>
      <td><button class="table-button" type="button" data-author-edit="${author.id}">編集</button><button class="table-button" type="button" data-author-books="${author.id}">書籍を見る</button></td>
    </tr>`).join("");
  renderAuthorCheckboxes();
}

function renderAuthorCheckboxes(selectedIds = selectedAuthorIds()) {
  const selected = new Set(selectedIds);
  byId("author-checkboxes").innerHTML = state.authors.map((author) => `
    <label><input type="checkbox" name="authorIds" value="${author.id}" ${selected.has(author.id) ? "checked" : ""}>${escapeHtml(author.name)} <span class="muted">(#${author.id})</span></label>`).join("");
  byId("authors-required").hidden = state.authors.length !== 0;
  byId("book-submit").disabled = state.authors.length === 0;
}

function selectedAuthorIds() {
  return [...document.querySelectorAll('input[name="authorIds"]:checked')].map((input) => Number(input.value));
}

function renderBooks() {
  byId("book-count").textContent = state.books.length;
  byId("books-empty").hidden = state.books.length !== 0;
  byId("books-table").innerHTML = state.books.map((book) => `
    <tr>
      <td>${book.id}</td><td>${escapeHtml(book.title)}</td><td>${price(book.price)}</td>
      <td class="authors-cell">${book.authors.map((author) => escapeHtml(author.name)).join("、")}</td>
      <td>${book.publicationStatus === "PUBLISHED" ? "出版済み" : "未出版"}</td>
      <td><button class="table-button" type="button" data-book-edit="${book.id}">編集</button></td>
    </tr>`).join("");
}

async function loadData() {
  const [authors, books] = await Promise.all([request("/authors"), request("/books")]);
  state.authors = authors;
  state.books = books;
  renderAuthors();
  renderBooks();
}

function resetAuthorForm() {
  byId("author-form").reset();
  byId("author-id").value = "";
  byId("author-form-title").textContent = "著者を登録";
  byId("author-submit").textContent = "登録する";
  byId("author-cancel").hidden = true;
}

function resetBookForm() {
  byId("book-form").reset();
  byId("book-id").value = "";
  byId("book-form-title").textContent = "書籍を登録";
  byId("book-submit").textContent = "登録する";
  byId("book-cancel").hidden = true;
  byId("publication-note").hidden = true;
  byId("book-status").querySelector('[value="UNPUBLISHED"]').disabled = false;
  renderAuthorCheckboxes([]);
}

function editAuthor(id) {
  const author = state.authors.find((item) => item.id === id);
  if (!author) return;
  byId("author-id").value = author.id;
  byId("author-name").value = author.name;
  byId("author-birth-date").value = author.birthDate;
  byId("author-form-title").textContent = `著者 #${author.id} を更新`;
  byId("author-submit").textContent = "更新する";
  byId("author-cancel").hidden = false;
  byId("author-name").focus();
}

function editBook(id) {
  const book = state.books.find((item) => item.id === id);
  if (!book) return;
  byId("book-id").value = book.id;
  byId("book-title").value = book.title;
  byId("book-price").value = book.price;
  byId("book-status").value = book.publicationStatus;
  const isPublished = book.publicationStatus === "PUBLISHED";
  byId("book-status").querySelector('[value="UNPUBLISHED"]').disabled = isPublished;
  byId("publication-note").hidden = !isPublished;
  renderAuthorCheckboxes(book.authors.map((author) => author.id));
  byId("book-form-title").textContent = `書籍 #${book.id} を更新`;
  byId("book-submit").textContent = "更新する";
  byId("book-cancel").hidden = false;
  byId("book-title").focus();
}

async function showAuthorBooks(id) {
  const author = state.authors.find((item) => item.id === id);
  if (!author) return;
  try {
    const books = await request(`/authors/${id}/books`);
    byId("author-books-title").textContent = `${author.name}さんの書籍`;
    byId("author-books-description").textContent = books.length === 0 ? "登録されている書籍はありません。" : `${books.length}件の書籍があります。`;
    byId("author-books-list").innerHTML = books.map((book) => `
      <article class="book-item"><strong>${escapeHtml(book.title)}</strong><span>価格: ${price(book.price)} / ${book.publicationStatus === "PUBLISHED" ? "出版済み" : "未出版"} / 著者: ${book.authors.map((item) => escapeHtml(item.name)).join("、")}</span></article>`).join("");
  } catch (error) { showNotice(error.message, true); }
}

function setActiveTab(tab) {
  document.querySelectorAll(".tab").forEach((button) => {
    const active = button.dataset.tab === tab;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-selected", String(active));
  });
  byId("authors-panel").hidden = tab !== "authors";
  byId("books-panel").hidden = tab !== "books";
}

document.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return;
  if (target.matches("[data-tab]")) setActiveTab(target.dataset.tab);
  if (target.matches("[data-author-edit]")) editAuthor(Number(target.dataset.authorEdit));
  if (target.matches("[data-author-books]")) showAuthorBooks(Number(target.dataset.authorBooks));
  if (target.matches("[data-book-edit]")) editBook(Number(target.dataset.bookEdit));
});

byId("author-cancel").addEventListener("click", resetAuthorForm);
byId("book-cancel").addEventListener("click", resetBookForm);

byId("author-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  clearNotice();
  const id = byId("author-id").value;
  const payload = { name: byId("author-name").value, birthDate: byId("author-birth-date").value };
  try {
    await request(id ? `/authors/${id}` : "/authors", {
      method: id ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
    });
    resetAuthorForm();
    await loadData();
    showNotice(id ? "著者を更新しました。" : "著者を登録しました。");
  } catch (error) { showNotice(error.message, true); }
});

byId("book-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  clearNotice();
  const id = byId("book-id").value;
  const payload = {
    title: byId("book-title").value,
    price: Number(byId("book-price").value),
    authorIds: selectedAuthorIds(),
    publicationStatus: byId("book-status").value,
  };
  try {
    await request(id ? `/books/${id}` : "/books", {
      method: id ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
    });
    resetBookForm();
    await loadData();
    showNotice(id ? "書籍を更新しました。" : "書籍を登録しました。");
  } catch (error) { showNotice(error.message, true); }
});

loadData().catch((error) => showNotice(`データを読み込めませんでした。${error.message}`, true));
