#include <gtk/gtk.h>
#include <webkit2/webkit2.h>
#include "kazview_native_core.h"

#include <chrono>
#include <cstring>
#include <iostream>
#include <string>
#include <vector>

struct BrowserApp {
    GtkWidget* window;
    GtkWidget* notebook;
    GtkWidget* url_entry;
    GtkWidget* desktop_btn;
    GtkWidget* back_btn;
    GtkWidget* fwd_btn;
    GtkWidget* progress_bar;
};

static BrowserApp g_app;

static long long current_epoch_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static std::string extract_host_ascii(const std::string& raw_url) {
    size_t scheme_pos = raw_url.find("://");
    size_t start = (scheme_pos == std::string::npos) ? 0 : (scheme_pos + 3);
    size_t end = raw_url.find_first_of(":/?#", start);
    if (end == std::string::npos) end = raw_url.size();
    if (end <= start) return "";
    return raw_url.substr(start, end - start);
}

static void apply_kazium_theme() {
    const char* css = R"(
        window { background-color: #08090C; color: #F0F4F8; }
        #toolbar { background-color: #0D0F14; padding: 6px 10px; border-bottom: 1px solid #1A1F2C; }
        progressbar trough { min-height: 2px; background-color: transparent; border: none; }
        progressbar progress { min-height: 2px; background-color: #00E5FF; }
        notebook { background-color: #08090C; border: none; }
        notebook header { background-color: #090B0F; border-bottom: 1px solid #181C26; padding: 2px 4px 0 4px; }
        notebook tab {
            background-color: #11141C;
            color: #8E99A8;
            border: 1px solid #1C2230;
            border-bottom: none;
            border-radius: 6px 6px 0 0;
            padding: 4px 10px;
            margin-right: 3px;
        }
        notebook tab:checked {
            background-color: #181D2A;
            color: #00E5FF;
            border-color: #00E5FF;
            font-weight: 600;
        }
        entry {
            background-color: #141721;
            color: #FFFFFF;
            border: 1px solid #23283A;
            border-radius: 8px;
            padding: 5px 12px;
            font-size: 13px;
        }
        entry:focus {
            border-color: #00E5FF;
            box-shadow: 0 0 0 1px #00E5FF;
        }
        button {
            background-color: #141721;
            color: #C5D1DE;
            border: 1px solid #23283A;
            border-radius: 6px;
            padding: 4px 10px;
            font-size: 12px;
        }
        button:hover {
            background-color: #1B2130;
            color: #00E5FF;
            border-color: #00E5FF;
        }
        #tab-close-btn {
            background: transparent;
            border: none;
            color: #8E99A8;
            padding: 0 4px;
            margin-left: 6px;
        }
        #tab-close-btn:hover {
            color: #FF5252;
        }
    )";

    GtkCssProvider* provider = gtk_css_provider_new();
    gtk_css_provider_load_from_data(provider, css, -1, nullptr);
    gtk_style_context_add_provider_for_screen(
        gdk_screen_get_default(),
        GTK_STYLE_PROVIDER(provider),
        GTK_STYLE_PROVIDER_PRIORITY_APPLICATION
    );
    g_object_unref(provider);
}

static WebKitWebView* get_active_web_view() {
    int current_page = gtk_notebook_get_current_page(GTK_NOTEBOOK(g_app.notebook));
    if (current_page < 0) return nullptr;
    GtkWidget* child = gtk_notebook_get_nth_page(GTK_NOTEBOOK(g_app.notebook), current_page);
    return WEBKIT_WEB_VIEW(child);
}

static void update_nav_buttons(WebKitWebView* web_view) {
    if (!web_view) return;
    gtk_widget_set_sensitive(g_app.back_btn, webkit_web_view_can_go_back(web_view));
    gtk_widget_set_sensitive(g_app.fwd_btn, webkit_web_view_can_go_forward(web_view));
}

static void update_progress_for_view(WebKitWebView* web_view) {
    if (!web_view || !g_app.progress_bar) return;
    gdouble progress = webkit_web_view_get_estimated_load_progress(web_view);
    if (webkit_web_view_is_loading(web_view) && progress < 1.0) {
        gtk_progress_bar_set_fraction(GTK_PROGRESS_BAR(g_app.progress_bar), progress > 0.08 ? progress : 0.08);
        gtk_widget_show(g_app.progress_bar);
    } else {
        gtk_widget_hide(g_app.progress_bar);
    }
}

static void on_url_activate(GtkEntry* entry, gpointer) {
    auto* web_view = get_active_web_view();
    if (!web_view) return;

    const char* text = gtk_entry_get_text(entry);
    std::string url(text ? text : "");
    if (url.empty()) return;

    if (url.find("://") == std::string::npos && url.find("kaspa:") == std::string::npos) {
        url = "https://" + url;
    }
    webkit_web_view_load_uri(web_view, url.c_str());
}

static void on_url_entry_changed(GtkEditable* editable, gpointer) {
    const char* raw = gtk_entry_get_text(GTK_ENTRY(editable));
    if (!raw || strlen(raw) < 4) return;
    std::string typed(raw);
    if (typed.find(' ') != std::string::npos) return;

    std::string candidate = (typed.rfind("https://", 0) == 0) ? typed
        : ((typed.find('.') != std::string::npos && typed.rfind("http://", 0) != 0) ? ("https://" + typed) : "");
    if (candidate.empty()) return;

    if (kazview_is_speculative_eligible(candidate.c_str(), false, false)) {
        std::string host = extract_host_ascii(candidate);
        std::string origin = "https://" + host;
        if (!host.empty() && kazview_record_origin_warmup(origin.c_str(), current_epoch_ms(), 60000)) {
            webkit_web_context_prefetch_dns(webkit_web_context_get_default(), host.c_str());
        }
    }
}

static void create_new_tab(const char* initial_url);

static void on_tab_close_clicked(GtkButton*, gpointer child) {
    int page_num = gtk_notebook_page_num(GTK_NOTEBOOK(g_app.notebook), GTK_WIDGET(child));
    if (page_num >= 0) {
        gtk_notebook_remove_page(GTK_NOTEBOOK(g_app.notebook), page_num);
    }
    kazview_reset_warmup_cache();
    if (gtk_notebook_get_n_pages(GTK_NOTEBOOK(g_app.notebook)) == 0) {
        create_new_tab("https://kaspa.org");
    }
}

static void on_title_changed(WebKitWebView* web_view, GParamSpec*, gpointer user_data) {
    auto* label = static_cast<GtkLabel*>(user_data);
    const char* title = webkit_web_view_get_title(web_view);
    if (title && strlen(title) > 0) {
        std::string short_title(title);
        if (short_title.length() > 22) {
            short_title = short_title.substr(0, 20) + "...";
        }
        gtk_label_set_text(label, short_title.c_str());
        gtk_widget_set_tooltip_text(GTK_WIDGET(label), title);
    }
}

static void on_progress_changed(WebKitWebView* web_view, GParamSpec*, gpointer) {
    if (web_view == get_active_web_view()) {
        update_progress_for_view(web_view);
    }
}

static void on_load_changed(WebKitWebView* web_view, WebKitLoadEvent load_event, gpointer) {
    if (web_view == get_active_web_view()) {
        update_nav_buttons(web_view);
        update_progress_for_view(web_view);
        if (load_event == WEBKIT_LOAD_COMMITTED) {
            const char* uri = webkit_web_view_get_uri(web_view);
            if (uri) {
                gtk_entry_set_text(GTK_ENTRY(g_app.url_entry), uri);
            }
        } else if (load_event == WEBKIT_LOAD_FINISHED) {
            gtk_widget_hide(g_app.progress_bar);
        }
    }
}

static gboolean on_load_failed(
    WebKitWebView* web_view,
    WebKitLoadEvent,
    gchar* failing_uri,
    GError* error,
    gpointer
) {
    const char* msg = (error && error->message) ? error->message : "Unable to load page";
    const char* safe_uri = failing_uri ? failing_uri : "https://kaspa.org";
    std::string html =
        "<!DOCTYPE html><html><head><meta charset='utf-8'>"
        "<style>body{background:#08090C;color:#E2E8F0;font-family:sans-serif;display:flex;"
        "align-items:center;justify-content:center;min-height:90vh;margin:0;}"
        ".card{background:#11141C;border:1px solid #23283A;border-radius:14px;padding:24px;"
        "max-width:440px;width:90%;}.btn{display:inline-block;margin-top:16px;padding:10px 18px;"
        "background:#00E5FF;color:#08090C;font-weight:700;border-radius:8px;text-decoration:none;}</style>"
        "</head><body><div class='card'><h2>Connection Interrupted</h2><p>" + std::string(msg) +
        "</p><p style='color:#00E5FF;word-break:break-all;'>" + std::string(safe_uri) +
        "</p><a class='btn' href='" + std::string(safe_uri) + "'>Retry</a></div></body></html>";
    webkit_web_view_load_alternate_html(web_view, html.c_str(), safe_uri, nullptr);
    return TRUE;
}

static void create_new_tab(const char* initial_url) {
    GtkWidget* web_view = webkit_web_view_new();
    WebKitSettings* settings = webkit_web_view_get_settings(WEBKIT_WEB_VIEW(web_view));
    webkit_settings_set_enable_developer_extras(settings, TRUE);
    webkit_settings_set_enable_webgl(settings, TRUE);
    webkit_settings_set_enable_accelerated_2d_canvas(settings, TRUE);
    webkit_settings_set_enable_page_cache(settings, TRUE);
    // Capability-aware GPU acceleration policy: ON_DEMAND avoids compositor crashes
    // on VMs/headless drivers while keeping full GPU speed when hardware supports it.
    webkit_settings_set_hardware_acceleration_policy(
        settings,
        WEBKIT_HARDWARE_ACCELERATION_POLICY_ON_DEMAND
    );
    webkit_settings_set_user_agent(
        settings,
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
    );

    // Match Kazium obsidian theme immediately before first webpage commit to eliminate white flashes
    GdkRGBA obsidian_bg = { 0.031, 0.035, 0.047, 1.0 };
    webkit_web_view_set_background_color(WEBKIT_WEB_VIEW(web_view), &obsidian_bg);

    GtkWidget* tab_box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 4);
    GtkWidget* title_label = gtk_label_new("Kaspa");
    gtk_box_pack_start(GTK_BOX(tab_box), title_label, FALSE, FALSE, 0);

    GtkWidget* close_btn = gtk_button_new_with_label("×");
    gtk_widget_set_name(close_btn, "tab-close-btn");
    g_signal_connect(close_btn, "clicked", G_CALLBACK(on_tab_close_clicked), web_view);
    gtk_box_pack_start(GTK_BOX(tab_box), close_btn, FALSE, FALSE, 0);
    gtk_widget_show_all(tab_box);

    g_signal_connect(web_view, "notify::title", G_CALLBACK(on_title_changed), title_label);
    g_signal_connect(web_view, "notify::estimated-load-progress", G_CALLBACK(on_progress_changed), nullptr);
    g_signal_connect(web_view, "load-changed", G_CALLBACK(on_load_changed), nullptr);
    g_signal_connect(web_view, "load-failed", G_CALLBACK(on_load_failed), nullptr);

    gtk_widget_show(web_view);
    int index = gtk_notebook_append_page(GTK_NOTEBOOK(g_app.notebook), web_view, tab_box);
    gtk_notebook_set_tab_reorderable(GTK_NOTEBOOK(g_app.notebook), web_view, TRUE);
    gtk_notebook_set_current_page(GTK_NOTEBOOK(g_app.notebook), index);

    const char* target = (initial_url && strlen(initial_url) > 0) ? initial_url : "https://kaspa.org";
    webkit_web_view_load_uri(WEBKIT_WEB_VIEW(web_view), target);
}

static void on_notebook_page_switched(GtkNotebook*, GtkWidget* page, guint, gpointer) {
    auto* web_view = WEBKIT_WEB_VIEW(page);
    const char* uri = webkit_web_view_get_uri(web_view);
    if (uri) {
        gtk_entry_set_text(GTK_ENTRY(g_app.url_entry), uri);
    } else {
        gtk_entry_set_text(GTK_ENTRY(g_app.url_entry), "");
    }
    update_nav_buttons(web_view);
    update_progress_for_view(web_view);
}

static void on_desktop_toggle(GtkButton* button, gpointer) {
    auto* web_view = get_active_web_view();
    if (!web_view) return;

    WebKitSettings* settings = webkit_web_view_get_settings(web_view);
    const char* current_ua = webkit_settings_get_user_agent(settings);
    bool is_desktop = (current_ua && strstr(current_ua, "X11; Linux x86_64") != nullptr);

    if (is_desktop) {
        webkit_settings_set_user_agent(
            settings,
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
        );
        gtk_button_set_label(button, "Mobile View");
    } else {
        webkit_settings_set_user_agent(
            settings,
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
        );
        gtk_button_set_label(button, "Desktop View");
    }
    webkit_web_view_reload(web_view);
}

int main(int argc, char* argv[]) {
    gtk_init(&argc, &argv);
    apply_kazium_theme();

    WebKitWebContext* web_context = webkit_web_context_get_default();
    webkit_web_context_set_cache_model(web_context, WEBKIT_CACHE_MODEL_WEB_BROWSER);
    webkit_web_context_prefetch_dns(web_context, "kaspa.org");

    g_app.window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_default_size(GTK_WINDOW(g_app.window), 1366, 860);
    gtk_window_set_title(GTK_WINDOW(g_app.window), "Kazium — Kazview WebKitGTK Multi-Tab Browser");

    g_signal_connect(g_app.window, "destroy", G_CALLBACK(gtk_main_quit), nullptr);

    GtkWidget* vbox = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
    gtk_container_add(GTK_CONTAINER(g_app.window), vbox);

    GtkWidget* toolbar = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 6);
    gtk_widget_set_name(toolbar, "toolbar");
    gtk_box_pack_start(GTK_BOX(vbox), toolbar, FALSE, FALSE, 0);

    g_app.back_btn = gtk_button_new_from_icon_name("go-previous", GTK_ICON_SIZE_BUTTON);
    g_app.fwd_btn = gtk_button_new_from_icon_name("go-next", GTK_ICON_SIZE_BUTTON);
    GtkWidget* reload_btn = gtk_button_new_from_icon_name("view-refresh", GTK_ICON_SIZE_BUTTON);
    GtkWidget* new_tab_btn = gtk_button_new_with_label("+ New Tab");
    GtkWidget* trim_btn = gtk_button_new_with_label("Trim Mem");
    g_app.desktop_btn = gtk_button_new_with_label("Desktop View");

    g_app.url_entry = gtk_entry_new();
    gtk_entry_set_placeholder_text(GTK_ENTRY(g_app.url_entry), "Search or enter Kaspa / Web URL...");

    gtk_box_pack_start(GTK_BOX(toolbar), g_app.back_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.fwd_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), reload_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.url_entry, TRUE, TRUE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), trim_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.desktop_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), new_tab_btn, FALSE, FALSE, 0);

    g_app.progress_bar = gtk_progress_bar_new();
    gtk_box_pack_start(GTK_BOX(vbox), g_app.progress_bar, FALSE, FALSE, 0);

    g_app.notebook = gtk_notebook_new();
    gtk_notebook_set_scrollable(GTK_NOTEBOOK(g_app.notebook), TRUE);
    gtk_box_pack_start(GTK_BOX(vbox), g_app.notebook, TRUE, TRUE, 0);

    g_signal_connect(g_app.url_entry, "activate", G_CALLBACK(on_url_activate), nullptr);
    g_signal_connect(g_app.url_entry, "changed", G_CALLBACK(on_url_entry_changed), nullptr);
    g_signal_connect(g_app.back_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        auto* wv = get_active_web_view();
        if (wv) webkit_web_view_go_back(wv);
    }), nullptr);
    g_signal_connect(g_app.fwd_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        auto* wv = get_active_web_view();
        if (wv) webkit_web_view_go_forward(wv);
    }), nullptr);
    g_signal_connect(reload_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        auto* wv = get_active_web_view();
        if (wv) webkit_web_view_reload(wv);
    }), nullptr);
    g_signal_connect(trim_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        kazview_reset_warmup_cache();
        webkit_web_context_clear_cache(webkit_web_context_get_default());
    }), nullptr);
    g_signal_connect(new_tab_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        create_new_tab("https://kaspa.org");
    }), nullptr);
    g_signal_connect(g_app.desktop_btn, "clicked", G_CALLBACK(on_desktop_toggle), nullptr);
    g_signal_connect(g_app.notebook, "switch-page", G_CALLBACK(on_notebook_page_switched), nullptr);

    const char* start_url = (argc > 1) ? argv[1] : "https://kaspa.org";
    create_new_tab(start_url);

    gtk_widget_show_all(g_app.window);
    gtk_widget_hide(g_app.progress_bar);
    gtk_main();
    return 0;
}
