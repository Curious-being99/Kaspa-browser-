#include "browser_host.h"
#include <atomic>
#include <poll.h>
#include <string>
#include <thread>
#include <unistd.h>
#include "include/cef_command_line.h"

namespace {
std::atomic<bool> stop_reader{false};
std::string InitialUrl(int argc, char* argv[]) {
  for (int i = 1; i < argc; ++i) {
    const std::string arg(argv[i]);
    const std::string prefix = "--url=";
    if (arg.rfind(prefix, 0) == 0) return arg.substr(prefix.size());
  }
  return "https://kaspabrowser.xyz";
}
void ReadCommands() {
  std::string pending;
  char buffer[1024];
  while (!stop_reader.load()) {
    pollfd fd{STDIN_FILENO, POLLIN | POLLHUP, 0};
    if (poll(&fd, 1, 100) <= 0) continue;
    const auto count = read(STDIN_FILENO, buffer, sizeof(buffer));
    if (count <= 0) break;
    pending.append(buffer, static_cast<size_t>(count));
    size_t end;
    while ((end = pending.find('\n')) != std::string::npos) {
      const std::string line = pending.substr(0, end);
      pending.erase(0, end + 1);
      if (line == "QUIT") { PostBrowserCommand("QUIT", ""); continue; }
      const auto tab = line.find('\t');
      PostBrowserCommand(line.substr(0, tab), tab == std::string::npos ? "" : line.substr(tab + 1));
    }
  }
}
}
int main(int argc, char* argv[]) {
  CefMainArgs args(argc, argv);
  const int subprocess = CefExecuteProcess(args, nullptr, nullptr);
  if (subprocess >= 0) return subprocess;
  CefSettings settings;
  settings.log_severity = LOGSEVERITY_WARNING;
  CefRefPtr<KaspaBrowserApp> app(new KaspaBrowserApp(InitialUrl(argc, argv)));
  if (!CefInitialize(args, settings, app, nullptr)) return CefGetExitCode();
  std::thread reader(ReadCommands);
  CefRunMessageLoop();
  stop_reader.store(true);
  reader.join();
  CefShutdown();
  return 0;
}
