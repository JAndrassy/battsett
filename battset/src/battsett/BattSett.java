package battsett;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class BattSett {

  private static int REGISTER_START = 40302 + 6;
  private static int REG_StorCtl_Mod = 6 - 6;
  private static int REG_OutWRte = 13 - 6;
  private static int REG_InWRte = 14 - 6;
  
  private Socket socket;

  public BattSett(String host, int port) throws Exception {
    socket = new Socket(host, port);
    socket.setKeepAlive(true);
  }

  public int[] modbusRead(int uid, int address, int len) throws IOException {
    OutputStream os = socket.getOutputStream();

    byte[] req = new byte[] { 0, 1, 0, 0, 0, 6, (byte) uid, 0x03, // header
        (byte) (address / 256), (byte) (address % 256), 0, (byte) len };

    os.write(req);
    os.flush();

    InputStream is = socket.getInputStream();
    is.skip(7); // ids
    int code = is.read();
    if (code == 0x83)
      throw new RuntimeException("modbus error " + is.read());
    if (code != 3)
      throw new RuntimeException("modbus response error fnc = " + code);
    int l = is.read() / 2;
    int[] response = new int[l];
    int i = 0;
    byte[] buff = new byte[2];
    while (true) {
      if (is.read(buff) == -1)
        break;
      response[i++] = buff[0] * 256 + buff[1];
      if (i == l)
        break;
    }
    return response;
  }

  public int modbusWriteSingle(int uid, int address, int val) throws IOException {
    OutputStream os = socket.getOutputStream();

    byte[] req = new byte[] { 0, 1, 0, 0, 0, 6, (byte) uid, 0x06, // header
        (byte) (address / 256), (byte) (address % 256), 
        (byte) (val / 256), (byte) (val % 256)};

    os.write(req);
    os.flush();

    InputStream is = socket.getInputStream();
    is.skip(7); // ids
    int code = is.read();
    if (code == 0x83)
      throw new RuntimeException("modbus error " + is.read());
    if (code != 6)
      throw new RuntimeException("modbus response error fnc = " + code);
    int response;
    byte[] buff = new byte[2];
    if (is.read(buff) == -1)  // address
      throw new RuntimeException("response error");
    if (is.read(buff) == -1)  // value
      throw new RuntimeException("response error");
    response = buff[0] * 256 + buff[1];
    return response;
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("usage: battset.jar <host>[:<port>] in|i|out|o [0%|<limit>[%]] [enable|1|disable|0]");
      System.exit(1);
      return;
    }
    String host = args[0];
    int port = 502;
    int p = host.indexOf(':');
    if (p > 0) {
      port = Integer.parseInt(host.substring(p + 1));
      host = host.substring(0,  p);
    }
    BattSett battset = new BattSett(host, port);
    int[] resp = battset.modbusRead(1, REGISTER_START, REG_InWRte + 1);
    if (args.length > 1) {
      char fn = Character.toLowerCase(args[1].charAt(0));
      if (fn != 'i' && fn != 'o') {
        System.err.println("invalid second argument");
        System.exit(1);
        return;
      }
      String arg = args.length > 2 ? args[2] : "0";
      int value = -1;
      boolean mode = true;
      switch (arg) {
        case "enable":
        case "1":
          break;
        case "disable":
        case "0":
          mode = false;
          break;
        case "0%":
          value = 0;
          break;
        default:
          if (arg.endsWith("%")) {
            arg = arg.substring(0, arg.length() - 1);
          }
          value = Integer.parseInt(arg);
          if (value < 0 || value > 100) {
            System.err.println("invalid limit percent");
            System.exit(1);
            return;
          }
          if (args.length > 3) {
            switch (args[3]) {
              case "enable":
              case "1":
                break;
              case "disable":
              case "0":
                mode = false;
                break;
              default:  
                System.err.println("invalid 4. argument");
                System.exit(1);
                return;
            }
          }
      }
      if (value >= 0) {
        battset.modbusWriteSingle(1, REGISTER_START + (fn == 'i' ? REG_InWRte : REG_OutWRte), value * 100);
      }
      int bits = resp[REG_StorCtl_Mod];
      int mask = (fn == 'i') ? 0b01 : 0b10;
      if (mode == true) {
        bits |= mask;
      } else {
        bits &= ~mask;
      }
      if (bits != resp[REG_StorCtl_Mod]) {
        battset.modbusWriteSingle(1, REGISTER_START, bits);
      }
      Thread.sleep(2000); // otherwise we would read old values
      resp = battset.modbusRead(1, REGISTER_START, REG_InWRte + 1);
    }
    
    System.out.println("Charge limit " + resp[REG_InWRte] / 100 + "% is " + bit2s(resp[REG_StorCtl_Mod], 0b01));
    System.out.println("Discharge limit " + resp[REG_OutWRte] / 100 + "% is " + bit2s(resp[REG_StorCtl_Mod], 0b10));

  }

  private static String bit2s(int value, int mask) {
    return (value & mask) != 0 ? "enabled" : "disabled";
  }

}
