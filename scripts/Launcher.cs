using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Text;
using System.Threading;
using System.Windows.Forms;

internal sealed class RentalLauncher : Form
{
    private readonly string root = AppDomain.CurrentDomain.BaseDirectory;
    private readonly Color orange = Color.FromArgb(207, 75, 10);
    private readonly Color ink = Color.FromArgb(18, 36, 64);
    private readonly Label status = new Label();
    private readonly Button start = new Button();
    private readonly Button open = new Button();
    private readonly Button stop = new Button();
    private readonly TextBox output = new TextBox();
    private readonly bool openAutomatically;
    private Process runner;
    private bool ready;
    private bool closing;

    [STAThread]
    private static void Main(string[] arguments)
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new RentalLauncher(Array.IndexOf(arguments, "--no-browser") < 0));
    }

    private RentalLauncher(bool browser)
    {
        openAutomatically = browser;
        Text = "易行租车 · 快速启动";
        ClientSize = new Size(620, 496);
        MinimumSize = new Size(636, 535);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Color.FromArgb(248, 249, 252);
        Font = new Font("Microsoft YaHei UI", 9F);
        AutoScaleMode = AutoScaleMode.Dpi;
        ForeColor = ink;
        Label heading = new Label { Text = "易行租车", AutoSize = true, Location = new Point(28, 23), Font = new Font(Font.FontFamily, 23F, FontStyle.Bold), ForeColor = orange };
        Label subtitle = new Label { Text = "本地工作空间  /  快速启动", AutoSize = true, Location = new Point(30, 76), ForeColor = Color.FromArgb(77, 96, 122) };
        status.SetBounds(30, 117, 560, 28);
        status.Text = "准备就绪，点击下方按钮启动系统。";
        status.Font = new Font(Font.FontFamily, 10F, FontStyle.Bold);
        status.AccessibleName = "启动状态";
        Label address = new Label { Text = "http://127.0.0.1:5173/", AutoSize = true, Location = new Point(30, 150), ForeColor = Color.FromArgb(37, 99, 235) };
        ConfigureButton(start, "启动并打开系统", 30, 190, 214);
        start.BackColor = orange;
        start.ForeColor = Color.White;
        start.Click += delegate { StartSystem(); };
        ConfigureButton(open, "打开网页", 256, 190, 150);
        open.Enabled = false;
        open.Click += delegate { OpenPage(); };
        ConfigureButton(stop, "停止本次服务", 418, 190, 172);
        stop.Enabled = false;
        stop.Click += delegate { StopSystem(); };
        output.SetBounds(30, 251, 560, 161);
        output.Anchor = AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Top | AnchorStyles.Bottom;
        output.Multiline = true;
        output.ReadOnly = true;
        output.ScrollBars = ScrollBars.Vertical;
        output.BorderStyle = BorderStyle.FixedSingle;
        output.BackColor = Color.White;
        output.ForeColor = Color.FromArgb(48, 66, 88);
        output.AccessibleName = "启动日志";
        output.Text = "首次运行自动安装依赖并构建，可能需要联网。" + Environment.NewLine + "若系统已经运行，将直接打开，不会重复启动。";
        LinkLabel logs = new LinkLabel { Text = "查看日志文件夹", AutoSize = true, Location = new Point(30, 428), Anchor = AnchorStyles.Bottom | AnchorStyles.Left, LinkColor = orange };
        logs.LinkClicked += delegate {
            string directory = Path.Combine(root, "data", "runtime");
            Directory.CreateDirectory(directory);
            ShellOpen(directory);
        };
        Label note = new Label { Text = "运行环境：Java 21 · Node.js 22 · Python 3；首次构建需 Maven 3.9+", AutoSize = true, Location = new Point(30, 464), Anchor = AnchorStyles.Bottom | AnchorStyles.Left, ForeColor = Color.FromArgb(77, 96, 122), Font = new Font(Font.FontFamily, 8F) };
        Controls.AddRange(new Control[] { heading, subtitle, status, address, start, open, stop, output, logs, note });
        FormClosing += OnClosing;
    }

    private void ConfigureButton(Button button, string text, int left, int top, int width)
    {
        button.Text = text;
        button.SetBounds(left, top, width, 42);
        button.FlatStyle = FlatStyle.Flat;
        button.FlatAppearance.BorderColor = Color.FromArgb(216, 224, 235);
        button.BackColor = Color.White;
        button.ForeColor = ink;
        button.Cursor = Cursors.Hand;
    }

    private void Dispatch(Action action)
    {
        if (!IsDisposed && IsHandleCreated)
            BeginInvoke(action);
    }

    private void Append(string text)
    {
        if (String.IsNullOrEmpty(text)) return;
        if (text.StartsWith("::ready::"))
        {
            ready = true;
            bool owned = text.EndsWith("owned");
            status.Text = owned ? "运行中 · 可通过本窗口停止本次服务" : "系统已在运行 · 已连接，不接管原有服务";
            status.ForeColor = Color.FromArgb(0, 133, 104);
            open.Enabled = true;
            stop.Enabled = owned;
            stop.Text = "停止本次服务";
            return;
        }
        if (output.TextLength > 50000) output.Text = output.Text.Substring(output.TextLength - 20000);
        output.AppendText(Environment.NewLine + text);
    }

    private string FindPython()
    {
        string local = Path.Combine(root, ".venv", "Scripts", "python.exe");
        if (File.Exists(local)) return local;
        foreach (string directory in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
        {
            try
            {
                string candidate = Path.Combine(directory.Trim('"'), "python.exe");
                if (File.Exists(candidate) && !candidate.Contains("WindowsApps")) return candidate;
            }
            catch (ArgumentException) { }
        }
        throw new InvalidOperationException("未找到 Python 3。请安装 Python 并勾选加入 PATH，然后重新打开启动器。");
    }

    private void StartSystem()
    {
        if (runner != null) return;
        try
        {
            if (!File.Exists(Path.Combine(root, "scripts", "run.py")))
                throw new InvalidOperationException("请把启动器保留在完整项目根目录，不要单独移动此 EXE。");
            ProcessStartInfo info = new ProcessStartInfo {
                FileName = FindPython(),
                Arguments = "-u scripts/run.py --managed" + (openAutomatically ? " --open" : ""),
                WorkingDirectory = root,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            info.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";
            Process process = new Process { StartInfo = info };
            process.OutputDataReceived += delegate(object sender, DataReceivedEventArgs arguments) { Dispatch(delegate { Append(arguments.Data); }); };
            process.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs arguments) { Dispatch(delegate { Append(arguments.Data); }); };
            process.Start();
            runner = process;
            ready = false;
            start.Enabled = false;
            open.Enabled = false;
            stop.Enabled = true;
            stop.Text = "取消启动";
            status.Text = "正在检查环境并启动，请稍候……";
            status.ForeColor = orange;
            output.Clear();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            Thread waiter = new Thread(delegate() {
                process.WaitForExit();
                int code = process.ExitCode;
                Dispatch(delegate {
                    runner = null;
                    start.Enabled = true;
                    stop.Enabled = false;
                    stop.Text = "停止本次服务";
                    if (code != 0) { status.Text = "启动未完成，请根据下方日志处理后重试。"; status.ForeColor = Color.Firebrick; }
                    else if (!ready) { status.Text = "本次启动的服务已停止。"; open.Enabled = false; }
                    process.Dispose();
                    if (closing) Close();
                });
            });
            waiter.IsBackground = true;
            waiter.Start();
        }
        catch (Exception error)
        {
            status.Text = "无法启动，请检查运行环境。";
            status.ForeColor = Color.Firebrick;
            Append(error.Message);
        }
    }

    private void StopSystem()
    {
        if (runner == null) return;
        try
        {
            ready = false;
            runner.StandardInput.WriteLine("stop");
            runner.StandardInput.Flush();
            stop.Enabled = false;
            status.Text = "正在停止本次启动的服务……";
        }
        catch (InvalidOperationException) { }
        catch (IOException error) { Append(error.Message); }
    }

    private void OpenPage() { ShellOpen("http://127.0.0.1:5173/"); }

    private void ShellOpen(string target)
    {
        try { Process.Start(new ProcessStartInfo(target) { UseShellExecute = true }); }
        catch (Exception error) { Append("无法打开：" + error.Message); }
    }

    private void OnClosing(object sender, FormClosingEventArgs arguments)
    {
        if (runner == null) return;
        arguments.Cancel = true;
        if (closing) return;
        if (MessageBox.Show(this, "退出将取消启动或停止由本窗口启动的服务。是否继续？", "退出启动器", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes) return;
        closing = true;
        StopSystem();
    }
}
