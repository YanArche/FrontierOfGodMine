using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;
class Bootstrap {
 [STAThread] static void Main() {
   try {
    var path=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"resources");
    var proc=new ProcessStartInfo(Path.Combine(path,"runtime","bin","javaw.exe"),"-Dfrontier.resources=\""+path+"\" -cp \""+Path.Combine(path,"frontier.jar")+";"+Path.Combine(path,"gson.jar")+"\" Frontier");
    proc.UseShellExecute=false;
    Process.Start(proc);
   } catch(Exception e){MessageBox.Show(e.Message,"Frontier of God");}
 }
}
