/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.io;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.apache.commons.logging.Log;

import org.apache.hadoop.conf.Configuration;

/**
 * An utility class for I/O related functionality. 
 */
public class IOUtils {
  
    /* @CS438
     * variables and datatype defined to sort blocks locally 
     */
    public static long MIN_MEMORY = 15000000;

    public enum ColDataType {
      INTEGER, STRINGS
    }


  /**
   * Copies from one stream to another.
   * @param in InputStrem to read from
   * @param out OutputStream to write to
   * @param buffSize the size of the buffer 
   * @param close whether or not close the InputStream and 
   * OutputStream at the end. The streams are closed in the finally clause.  
   */
  public static void copyBytes(InputStream in, OutputStream out, int buffSize, boolean close) 
    throws IOException {

    try {
      copyBytes(in, out, buffSize);
    } finally {
      if(close) {
        out.close();
        in.close();
      }
    }
  }
  
  /**
   * Copies from one stream to another.
   * 
   * @param in InputStrem to read from
   * @param out OutputStream to write to
   * @param buffSize the size of the buffer 
   */
  public static void copyBytes(InputStream in, OutputStream out, int buffSize) 
    throws IOException {

    PrintStream ps = out instanceof PrintStream ? (PrintStream)out : null;
    byte buf[] = new byte[buffSize];
    int bytesRead = in.read(buf);
    while (bytesRead >= 0) {
      out.write(buf, 0, bytesRead);
      if ((ps != null) && ps.checkError()) {
        throw new IOException("Unable to write to output stream.");
      }
      bytesRead = in.read(buf);
    }
  }

  /**
   * Copies from one stream to another. <strong>closes the input and output streams 
   * at the end</strong>.
   * @param in InputStrem to read from
   * @param out OutputStream to write to
   * @param conf the Configuration object 
   */
  public static void copyBytes(InputStream in, OutputStream out, Configuration conf)
    throws IOException {
    copyBytes(in, out, conf.getInt("io.file.buffer.size", 4096), true);
  }
  
  /**
   * Copies from one stream to another.
   * @param in InputStrem to read from
   * @param out OutputStream to write to
   * @param conf the Configuration object
   * @param close whether or not close the InputStream and 
   * OutputStream at the end. The streams are closed in the finally clause.
   */
  public static void copyBytes(InputStream in, OutputStream out, Configuration conf, boolean close)
    throws IOException {
    copyBytes(in, out, conf.getInt("io.file.buffer.size", 4096),  close);
  }
  
  /**
   * Copies the specified length of bytes from in to out.
   *
   * @param in InputStream to read from
   * @param out OutputStream to write to
   * @param length number of bytes to copy
   * @param bufferSize the size of the buffer 
   * @param close whether to close the streams
   * @throws IOException if bytes can not be read or written
   */
  public static void copyBytes(InputStream in, OutputStream out,
      final long length, final int bufferSize, final boolean close
      ) throws IOException {
    final byte buf[] = new byte[bufferSize];
    try {
      int n = 0;
      for(long remaining = length; remaining > 0 && n != -1; remaining -= n) {
        final int toRead = remaining < buf.length? (int)remaining : buf.length;
        n = in.read(buf, 0, toRead);
        if (n > 0) {
          out.write(buf, 0, n);
        }
      }

      if (close) {
        out.close();
        out = null;
        in.close();
        in = null;
      }
    } finally {
      if (close) {
        closeStream(out);
        closeStream(in);
      }
    }
  }

  /** Reads len bytes in a loop.
   * @param in The InputStream to read from
   * @param buf The buffer to fill
   * @param off offset from the buffer
   * @param len the length of bytes to read
   * @throws IOException if it could not read requested number of bytes 
   * for any reason (including EOF)
   */
  public static void readFully( InputStream in, byte buf[],
      int off, int len ) throws IOException {
    int toRead = len;
    while ( toRead > 0 ) {
      int ret = in.read( buf, off, toRead );
      if ( ret < 0 ) {
        throw new IOException( "Premature EOF from inputStream");
      }
      toRead -= ret;
      off += ret;
    }
  }

  /** Reads len bytes in a loop using the channel of the stream
   * @param fileChannel a FileChannel to read len bytes into buf
   * @param buf The buffer to fill
   * @param off offset from the buffer
   * @param len the length of bytes to read
   * @throws IOException if it could not read requested number of bytes 
   * for any reason (including EOF)
   */
  public static void readFileChannelFully( FileChannel fileChannel, byte buf[],
      int off, int len ) throws IOException {
    int toRead = len;
    ByteBuffer byteBuffer = ByteBuffer.wrap(buf, off, len);
    while ( toRead > 0 ) {
      int ret = fileChannel.read(byteBuffer);
      if ( ret < 0 ) {
        throw new IOException( "Premeture EOF from inputStream");
      }
      toRead -= ret;
      off += ret;
    }
  }
  
  /** Similar to readFully(). Skips bytes in a loop.
   * @param in The InputStream to skip bytes from
   * @param len number of bytes to skip.
   * @throws IOException if it could not skip requested number of bytes 
   * for any reason (including EOF)
   */
  public static void skipFully( InputStream in, long len ) throws IOException {
    while ( len > 0 ) {
      long ret = in.skip( len );
      if ( ret < 0 ) {
        throw new IOException( "Premature EOF from inputStream");
      }
      len -= ret;
    }
  }
  
  /**
   * Close the Closeable objects and <b>ignore</b> any {@link IOException} or 
   * null pointers. Must only be used for cleanup in exception handlers.
   * @param log the log to record problems to at debug level. Can be null.
   * @param closeables the objects to close
   */
  public static void cleanup(Log log, java.io.Closeable... closeables) {
    for(java.io.Closeable c : closeables) {
      if (c != null) {
        try {
          c.close();
        } catch(IOException e) {
          if (log != null && log.isDebugEnabled()) {
            log.debug("Exception in closing " + c, e);
          }
        }
      }
    }
  }

  /**
   * Closes the stream ignoring {@link IOException}.
   * Must only be called in cleaning up from exception handlers.
   * @param stream the Stream to close
   */
  public static void closeStream( java.io.Closeable stream ) {
    cleanup(null, stream);
  }
  
  /**
   * Closes the socket ignoring {@link IOException} 
   * @param sock the Socket to close
   */
  public static void closeSocket( Socket sock ) {
    // avoids try { close() } dance
    if ( sock != null ) {
      try {
       sock.close();
      } catch ( IOException ignored ) {
      }
    }
  }
  
  /** /dev/null of OutputStreams.
   */
  public static class NullOutputStream extends OutputStream {
    public void write(byte[] b, int off, int len) throws IOException {
    }

    public void write(int b) throws IOException {
    }
  }  

  /* 
   * @CS438
   * @param filename The name of the file of the block to be replicated.
   * @param sep the separator for the file. "," for our purposes.
   * @param col Represents the column to sort on. 
   * @param ColDataType the type of data in the given column. We use String and Integer.
   * This function is responsible for sorting a block on a specific column. 
   */
  public void sortBlock(String filename, String sep, int col, ColDataType type) throws Exception {   
		//used to identify intermediate files
  	long fileNum = 987654321;

		Runtime r = Runtime.getRuntime();
		long freeMemory = r.freeMemory();

		BufferedReader in = new BufferedReader(new FileReader(filename));
		ArrayList<String> lines = new ArrayList<String>();
		String inputLine = "", target = "";

		int counter = 0, len = 0;
		int index;
    
		while ((inputLine = in.readLine()) != null) {
			target = inputLine;	
      
      /* Here we append the sort key to the beginning of the string. This is done to reduce usage
       * of substring and we gain a 25% speedup.  
       */
			for(int i = 0; i < col; i++)
				target = target.substring(target.indexOf(sep)+1);
        
			index = target.indexOf(sep);
			if (index != -1)
				target = target.substring(0, index);
        
			lines.add(target + sep + inputLine);
			counter++;

      // if false, means we're running low, so we opt to write to disk instead. 
			if(r.freeMemory() > IOUtils.MIN_MEMORY)      
				continue;

			String[] lineArray = new String[counter];
			lines.toArray(lineArray);
			InputCompare<String> comp = new InputCompare<String>(sep, col, type);
			Arrays.sort(lineArray, comp);

			ConcurrentSortedWriter csw = new ConcurrentSortedWriter(filename+"s"+fileNum++, lineArray,sep);
			csw.start();
			lines.clear();
			System.gc();
			counter = 0;
		}

		String[] lineArray = new String[counter];
 		lines.toArray(lineArray); 
		InputCompare<String> comp = new InputCompare<String>(sep, col, type);
		Arrays.sort(lineArray);
	
		ConcurrentSortedWriter csw = new ConcurrentSortedWriter(filename+"s"+fileNum++, lineArray, sep);
		csw.start();
		lines.clear();
		in.close();
    
    /* @CS438 TODO: Implement n-way merge for the intermediate files. 
     */
	}

	public class ConcurrentSortedWriter extends Thread { 
		String filename;
		String[] arr;
		String sep;

		public ConcurrentSortedWriter(String filename, String[] arr, String sep) {
			this.filename = filename;
			this.arr = arr;
			this.sep = sep;
		}

		public void run() {
			PrintWriter out = null; 
			try {
				out = new PrintWriter(new FileWriter(filename));
				for(int i = 0; i < arr.length; i++) 
					out.println(arr[i].substring(arr[i].indexOf(sep)+1));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			finally {
				if(out != null)
					out.close();
			}
		}
	}

	public class InputCompare<T> implements Comparator<T> {

		private String separator;
		private int col = 0;
		private ColDataType type = ColDataType.STRINGS;

		public InputCompare(String separator, int col, ColDataType type) {
			this.separator = separator;
			this.col = col;
			this.type = type;
		}

		public int compare(T o1, T o2) {
			if (o1 instanceof String && o2 instanceof String) {
				String s1 = (String)o1;
				String s2 = (String)o2;
        
				for(int i = 0; i < col; i++) {
					s1 = s1.substring(s1.indexOf(separator)+1);
					s2 = s2.substring(s2.indexOf(separator)+1);
				}
        
				int ind1 = s1.indexOf(separator);
				int ind2 = s2.indexOf(separator);
        
				if (ind1 != -1)
					s1 = s1.substring(0, ind1);
				if (ind2 != -1)
					s2 = s2.substring(0, ind2);
				
				switch (type) {
					case INTEGER:
						return Integer.parseInt(s1) - Integer.parseInt(s2);
					case STRINGS:
					default:
						return s1.compareTo(s2);
				}
			}
		}

		public boolean equals(T o1, T o2) {
			return compare(o1, o2) == 0;
		}
	}
}