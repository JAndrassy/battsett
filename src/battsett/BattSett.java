/*
Copyright (C) 2017 Juraj Andrássy
repository https://github.com/jandrassy/battsett

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/   

package battsett;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class BattSett {

  static int FNC_READ_REGS = 0x03;
  static int FNC_WRITE_SINGLE = 0x06;
  static int FNC_ERR_FLAG = 0x80;
  static int ERR_SLAVE_DEVICE_FAILURE = 4;

  private static int ADDR_MODEL = 215;
  private static int ADDR_INTSF = 40303;
  private static int ADDR_FLOAT = 40313;
  private static int REG_StorCtl_Mod = 5;
  private static int REG_OutWRte = 12;
  private static int REG_InWRte = 13;
  private static int REG_InOutWRte_SF = 25;
  private static int REGISTER_LENGTH = REG_InOutWRte_SF + 1;

  private Socket socket;

  public BattSett(String host, int port) throws Exception {
    socket = new Socket(host, port);
    socket.setKeepAlive(true);
  }

  public int[] modbusRead(int uid, int address, int len) throws IOException {
    OutputStream os = socket.getOutputStream();

    byte[] req = new byte[] { 0, 1, 0, 0, 0, 6, (byte) uid, (byte) FNC_READ_REGS, // header
        (byte) (address / 256), (byte) (address % 256), 0, (byte) len };

    os.write(req);
    os.flush();

    InputStream is = socket.getInputStream();
    is.skip(7); // ids
    int code = is.read();
    if (code == (FNC_ERR_FLAG | FNC_READ_REGS)) 
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
      int hi = buff[0] & 0xFF;
      int lo = buff[1] & 0xFF;
      response[i++] = hi * 256 + lo;
      if (i == l)
        break;
    }
    return response;
  }

  public int modbusWriteSingle(int uid, int address, int val) throws IOException {
    OutputStream os = socket.getOutputStream();

    byte[] req = new byte[] { 0, 1, 0, 0, 0, 6, (byte) uid, (byte) FNC_WRITE_SINGLE, // header
        (byte) (address / 256), (byte) (address % 256), 
        (byte) (val / 256), (byte) (val % 256)};

    os.write(req);
    os.flush();

    InputStream is = socket.getInputStream();
    is.skip(7); // ids
    int code = is.read();
    if (code == (FNC_ERR_FLAG | FNC_WRITE_SINGLE)) {
      int ec = is.read();
      if (ec == ERR_SLAVE_DEVICE_FAILURE)
        throw new RuntimeException("Modbus error. Check if 'Inverter control via Modbus' is enabled.");
      throw new RuntimeException("modbus error " + ec);
    }
    if (code != 6)
      throw new RuntimeException("modbus response error fnc = " + code);
    int response;
    byte[] buff = new byte[2];
    if (is.read(buff) == -1)  // address
      throw new RuntimeException("response error");
    if (is.read(buff) == -1)  // value
      throw new RuntimeException("response error");
    int hi = buff[0] & 0xFF;
    int lo = buff[1] & 0xFF;
    response = hi * 256 + lo;
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
    int[] resp = battset.modbusRead(1, ADDR_MODEL, 1);
    int baseAddr = (resp[0] == 2) ? ADDR_INTSF : ADDR_FLOAT;
    resp = battset.modbusRead(1, baseAddr, REGISTER_LENGTH);
    int storCtrlMod = resp[REG_StorCtl_Mod];
    double sf = Math.pow(10, (short) resp[REG_InOutWRte_SF]);
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
        battset.modbusWriteSingle(1, baseAddr + (fn == 'i' ? REG_InWRte : REG_OutWRte), (int) (value / sf));
      }
      int bits = storCtrlMod;
      int mask = (fn == 'i') ? 0b01 : 0b10;
      if (mode == true) {
        bits |= mask;
      } else {
        bits &= ~mask;
      }
      if (bits != storCtrlMod) {
        battset.modbusWriteSingle(1, baseAddr + REG_StorCtl_Mod, bits);
      }
      Thread.sleep(2000); // otherwise we would read old values
      resp = battset.modbusRead(1, baseAddr, REGISTER_LENGTH);
      storCtrlMod = resp[REG_StorCtl_Mod];
      sf = Math.pow(10, (short) resp[REG_InOutWRte_SF]);
    }
    
    System.out.println("Charge limit " + (int) (resp[REG_InWRte] * sf) + "% is " + bit2s(storCtrlMod, 0b01));
    System.out.println("Discharge limit " + (int) (resp[REG_OutWRte] * sf) + "% is " + bit2s(storCtrlMod, 0b10));

  }

  static String bit2s(int value, int mask) {
    return (value & mask) != 0 ? "enabled" : "disabled";
  }

}
