#include <gtk/gtk.h>
#include <webkit2/webkit2.h>
#include <string>
#include <iostream>
#include <vector>

struct TabInfo {
    GtkWidget* web_view;
    GtkWidget* tab_box;
    GtkWidget* title_label;
    GtkWidget* close_btn;
    std::string current_url;
    bool is_desktop_mode;
};

struct BrowserApp {
    GtkWidget* window;
    GtkWidget* notebook;
    GtkWidget* url_entry;
    GtkWidget* desktop_btn;
    GtkWidget* back_btn;
    GtkWidget* fwd_btn;
};

static BrowserApp g_app;

static void apply_kazium_theme() {
    const char* css = R"(
        window { background-color: #08090C; color: #F0F4F8; }
        #toolbar { background-color: #0D0F14; padding: 6px 10px; border-bottom: 1px solid #1A1F2C; }
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

static void create_new_tab(const char* initial_url);

static void on_tab_close_clicked(GtkButton*, gpointer child) {
    int page_num = gtk_notebook_page_num(GTK_NOTEBOOK(g_app.notebook), GTK_WIDGET(child));
    if (page_num >= 0) {
        gtk_notebook_remove_page(GTK_NOTEBOOK(g_app.notebook), page_num);
    }
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

static void on_load_changed(WebKitWebView* web_view, WebKitLoadEvent load_event, gpointer) {
    if (web_view == get_active_web_view()) {
        update_nav_buttons(web_view);
        if (load_event == WEBKIT_LOAD_COMMITTED) {
            const char* uri = webkit_web_view_get_uri(web_view);
            if (uri) {
                gtk_entry_set_text(GTK_ENTRY(g_app.url_entry), uri);
            }
        }
    }
}

static void create_new_tab(const char* initial_url) {
    GtkWidget* web_view = webkit_web_view_new();
    WebKitSettings* settings = webkit_web_view_get_settings(WEBKIT_WEB_VIEW(web_view));
    webkit_settings_set_enable_developer_extras(settings, TRUE);
    webkit_settings_set_enable_webgl(settings, TRUE);
    webkit_settings_set_enable_accelerated_2d_canvas(settings, TRUE);
    webkit_settings_set_user_agent(settings, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15");

    // Custom Tab Label with Title and Close Button
    GtkWidget* tab_box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 4);
    GtkWidget* title_label = gtk_label_new("Kaspa");
    gtk_box_pack_start(GTK_BOX(tab_box), title_label, FALSE, FALSE, 0);

    GtkWidget* close_btn = gtk_button_new_with_label("×");
    gtk_widget_set_name(close_btn, "tab-close-btn");
    g_signal_connect(close_btn, "clicked", G_CALLBACK(on_tab_close_clicked), web_view);
    gtk_box_pack_start(GTK_BOX(tab_box), close_btn, FALSE, FALSE, 0);
    gtk_widget_show_all(tab_box);

    g_signal_connect(web_view, "notify::title", G_CALLBACK(on_title_changed), title_label);
    g_signal_connect(web_view, "load-changed", G_CALLBACK(on_load_changed), nullptr);

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
}

static void on_desktop_toggle(GtkButton* button, gpointer) {
    auto* web_view = get_active_web_view();
    if (!web_view) return;

    WebKitSettings* settings = webkit_web_view_get_settings(web_view);
    const char* current_ua = webkit_settings_get_user_agent(settings);
    bool is_desktop = (current_ua && strstr(current_ua, "X11; Linux x86_64") != nullptr);

    if (is_desktop) {
        webkit_settings_set_user_agent(settings, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");
        gtk_button_set_label(button, "Mobile View");
    } else {
        webkit_settings_set_user_agent(settings, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15");
        gtk_button_set_label(button, "Desktop View");
    }
    webkit_web_view_reload(web_view);
}

int main(int argc, char* argv[]) {
    gtk_init(&argc, &argv);
    apply_kazium_theme();

    g_app.window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_default_size(GTK_WINDOW(g_app.window), 1366, 860);
    gtk_window_set_title(GTK_WINDOW(g_app.window), "Kazium — Kazview WebKitGTK Multi-Tab Browser");

    g_signal_connect(g_app.window, "destroy", G_CALLBACK(gtk_main_quit), nullptr);

    GtkWidget* vbox = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
    gtk_container_add(GTK_CONTAINER(g_app.window), vbox);

    // Top Navigation Toolbar
    GtkWidget* toolbar = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 6);
    gtk_widget_set_name(toolbar, "toolbar");
    gtk_box_pack_start(GTK_BOX(vbox), toolbar, FALSE, FALSE, 0);

    g_app.back_btn = gtk_button_new_from_icon_name("go-previous", GTK_ICON_SIZE_BUTTON);
    g_app.fwd_btn = gtk_button_new_from_icon_name("go-next", GTK_ICON_SIZE_BUTTON);
    GtkWidget* reload_btn = gtk_button_new_from_icon_name("view-refresh", GTK_ICON_SIZE_BUTTON);
    GtkWidget* new_tab_btn = gtk_button_new_with_label("+ New Tab");
    g_app.desktop_btn = gtk_button_new_with_label("Desktop View");

    g_app.url_entry = gtk_entry_new();
    gtk_entry_set_placeholder_text(GTK_ENTRY(g_app.url_entry), "Search or enter Kaspa / Web URL...");

    gtk_box_pack_start(GTK_BOX(toolbar), g_app.back_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.fwd_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), reload_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.url_entry, TRUE, TRUE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), g_app.desktop_btn, FALSE, FALSE, 0);
    gtk_box_pack_start(GTK_BOX(toolbar), new_tab_btn, FALSE, FALSE, 0);

    // Multi-Tab GtkNotebook
    g_app.notebook = gtk_notebook_new();
    gtk_notebook_set_scrollable(GTK_NOTEBOOK(g_app.notebook), TRUE);
    gtk_box_pack_start(GTK_BOX(vbox), g_app.notebook, TRUE, TRUE, 0);

    g_signal_connect(g_app.url_entry, "activate", G_CALLBACK(on_url_activate), nullptr);
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
    g_signal_connect(new_tab_btn, "clicked", G_CALLBACK(+[](GtkButton*, gpointer){
        create_new_tab("https://kaspa.org");
    }), nullptr);
    g_signal_connect(g_app.desktop_btn, "clicked", G_CALLBACK(on_desktop_toggle), nullptr);
    g_signal_connect(g_app.notebook, "switch-page", G_CALLBACK(on_notebook_page_switched), nullptr);

    // Open Initial Tab
    const char* start_url = (argc > 1) ? argv[1] : "https://kaspa.org";
    create_new_tab(start_url);

    gtk_widget_show_all(g_app.window);
    gtk_main();
    return 0;
}
