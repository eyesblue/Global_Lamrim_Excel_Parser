import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JOptionPane;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;




import com.csvreader.CsvReader;

public class Downloader {
	static String url="http://lamrimreader.gebis.global/appresources/globallamrim/GlobalLamrimSchedule.CSV?attredirects=0&d=1";
	Hashtable<String,GlRecord> glSchedule=new Hashtable<String,GlRecord>();
	SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy/MM/dd");
	static File dlTmpFile=null;
	
	public Hashtable<String,GlRecord> getGlSchedule(){
		return glSchedule;
	}
	
	public File getDownloadTmpFile(){
		return dlTmpFile;
	}
	
	public boolean reloadSchedule() {
		CsvReader csvr=null;
		Date glRangeStart,glRangeEnd;
		// Load the date range of file.
		try {
			csvr=new CsvReader(dlTmpFile.getAbsolutePath(),',', Charset.forName("UTF-8"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "找不到已下載的檔案！", "檔案開啟失敗", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		try {
			if(!csvr.readRecord()){
				JOptionPane.showMessageDialog(null, "讀取已下載的進度檔案失敗！", "檔案開啟失敗", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			int count=csvr.getColumnCount();
			if(count<2){
				JOptionPane.showMessageDialog(null, "在已下載的進度中讀取日期範圍失敗！", "檔案讀取失敗", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			DateFormat df = DateFormat.getDateInstance();
			try {
				Date arg1=df.parse(csvr.get(0));
				Date arg2=df.parse(csvr.get(1));
				if(arg1.getTime()<arg2.getTime()){
					glRangeStart=arg1;
					glRangeEnd=arg2;
				}else{
					glRangeStart=arg2;
					glRangeEnd=arg1;
				}
			} catch (ParseException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, "已下載的進度檔案CSV格式錯誤！", "檔案格式錯誤", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "讀取已下載的檔案時發生錯誤！", "檔案讀取錯誤", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		try {
			while(csvr.readRecord()){
				int count=csvr.getColumnCount();
				GlRecord glr=new GlRecord();

				glr.dateStart=csvr.get(0);
				glr.dateEnd=csvr.get(1);
				glr.speechPositionStart=csvr.get(2);
				glr.speechPositionEnd=csvr.get(3);
				glr.totalTime=csvr.get(4);
				glr.theoryLineStart=csvr.get(5);
				glr.theoryLineEnd=csvr.get(6);
				glr.subtitleLineStart=csvr.get(7);
				glr.subtitleLineEnd=csvr.get(8);
				glr.desc=csvr.get(9);
				addGlRecord(glr);
			}
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "已下載的進度檔案CSV格式錯誤！", "檔案格式錯誤", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		System.out.println("Total records: "+glSchedule.size());
		return true;
	}
	
	public void addGlRecord(GlRecord glr){
		glSchedule.put(glr.dateStart, glr);
		System.out.println("Add record: key="+glr.dateStart+", data="+glr);
	}
	
	public boolean downloadSchedule() throws IOException {
		//String url="http://lamrimreader.eyes-blue.com/appresources/globallamrim/GlobalLamrimSchedule.CSV?attredirects=0&d=1";
		dlTmpFile=File.createTempFile("GLS", "tmp");
		
		System.out.println("Download "+url);
		HttpClient httpclient = getNewHttpClient();
		HttpGet httpget = new HttpGet(url);
		HttpResponse response=null;
		int respCode=-1;

		try {
			response = httpclient.execute(httpget);
			respCode=response.getStatusLine().getStatusCode();
			if(respCode!=HttpStatus.SC_OK){
				httpclient.getConnectionManager().shutdown();
				JOptionPane.showMessageDialog(null, "無法成功連線到廣論App網頁，請檢查您的網路環境是否連通。", "連線失敗", JOptionPane.ERROR_MESSAGE);
				return false;
	        }
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "無法成功連線到廣論App網頁，請檢查您的網路環境是否連通。", "連線失敗", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		System.out.println("Connect success, Downloading file.");
		HttpEntity httpEntity=response.getEntity();
		InputStream is=null;
		try {
			is = httpEntity.getContent();
		} catch (IllegalStateException e2) {
			try {   is.close();     } catch (IOException e) {e.printStackTrace();}
			httpclient.getConnectionManager().shutdown();
			e2.printStackTrace();
			JOptionPane.showMessageDialog(null, "檔案下載失敗，請檢查廣論App全廣進度檔案是否存在於網頁中。", "下載失敗", JOptionPane.ERROR_MESSAGE);
			return false;
		} catch (IOException e2) {
			httpclient.getConnectionManager().shutdown();
			e2.printStackTrace();
			JOptionPane.showMessageDialog(null, "檔案下載失敗，請檢查廣論App全廣進度檔案是否存在於網頁中。", "下載失敗", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		final long contentLength=httpEntity.getContentLength();
		System.out.println("Content length: "+contentLength);
		FileOutputStream fos=null;
		int counter=0;
		try {
			System.out.println("Create download temp file: "+dlTmpFile);
			fos=new FileOutputStream(dlTmpFile);
		} catch (FileNotFoundException e) {e.printStackTrace();return false;}
		
		try {
			byte[] buf=new byte[32768];
			int readLen=0;
			System.out.println(Thread.currentThread().getName()+": Start read stream from remote site, is="+((is==null)?"NULL":"exist")+", buf="+((buf==null)?"NULL":"exist"));
			while((readLen=is.read(buf))!=-1){
				counter+=readLen;
				fos.write(buf,0,readLen);
            }
			is.close();
			fos.flush();
			fos.close();
		} catch (IOException e) {
			httpclient.getConnectionManager().shutdown();
			try {   is.close();     } catch (IOException e2) {e2.printStackTrace();return false;}
			try {   fos.close();    } catch (IOException e2) {e2.printStackTrace();return false;}
			dlTmpFile.delete();
			e.printStackTrace();
			System.out.println(Thread.currentThread().getName()+": IOException happen while download media.");
			JOptionPane.showMessageDialog(null, "檔案下載失敗，請檢查您的網路環境是否連通。", "下載失敗", JOptionPane.ERROR_MESSAGE);
			return true;
		}
		
/*		if(counter!=contentLength){
			httpclient.getConnectionManager().shutdown();
			tmpFile.delete();
			showNarmalToastMsg(getString(R.string.dlgDescDownloadFail));
			downloadPDialog.dismiss();
			return;
		}
*/		
		// rename the protected file name to correct file name

        httpclient.getConnectionManager().shutdown();
        System.out.println(Thread.currentThread().getName()+": Download finish.");
		return true;
	}
	
	
	private HttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }
	
	public class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[] { tm }, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }
}
