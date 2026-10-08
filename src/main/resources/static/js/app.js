// Sends the CSRF token with every htmx request.
document.addEventListener('htmx:configRequest', function (event) {
    var token = document.querySelector('meta[name="csrf-token"]');
    var header = document.querySelector('meta[name="csrf-header"]');
    if (token && header) {
        event.detail.headers[header.content] = token.content;
    }
});
