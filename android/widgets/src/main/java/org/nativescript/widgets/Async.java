package org.nativescript.widgets;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Base64;

import java.net.CookieManager;

public class Async
{
	public interface CompleteCallback {
		void onComplete(Object result, Object tag);
	}

	public static class Image {
		/*
		* The request id parameter is needed for the sake of the JavaScript implementation.
		* Because we want to use only one extend of the CompleteCallback interface (for the sake of better performance)
		* we use this id to detect the initial request, which result is currently received in the complete callback.
		* When the async task completes it will pass back this id to JavaScript.
		*/
		public static void fromResource(String name, Context context, int requestId, CompleteCallback callback) {
			new LoadImageFromResourceTask(context, requestId, callback).execute(name);
		}

		public static void fromFile(String fileName, int requestId, CompleteCallback callback) {
			new LoadImageFromFileTask(requestId, callback).execute(fileName);
		}

		public static void fromBase64(String source, int requestId, CompleteCallback callback) {
			new LoadImageFromBase64StringTask(requestId, callback).execute(source);
		}

		public static void download(String url, CompleteCallback callback, Object context) {
			new DownloadImageTask(callback, context).execute(url);
		}

		static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
			private CompleteCallback callback;
			private Object context;

			public DownloadImageTask(CompleteCallback callback, Object context) {
				this.callback = callback;
				this.context = context;
			}

			protected Bitmap doInBackground(String... params) {
				InputStream stream = null;
				try {
					stream = new java.net.URL(params[0]).openStream();
					Bitmap bmp = BitmapFactory.decodeStream(stream);
					return bmp;
				} catch (MalformedURLException e) {
					e.printStackTrace();
					return null;
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				} finally {
					if (stream != null) {
						try {
							stream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}

			protected void onPostExecute(final Bitmap result) {
				this.callback.onComplete(result, this.context);
			}
		}

		static class LoadImageFromResourceTask extends AsyncTask<String, Void, Bitmap> {
			private CompleteCallback callback;
			private Context context;
			private int requestId;

			public LoadImageFromResourceTask(Context context, int requestId, CompleteCallback callback) {
				this.callback = callback;
				this.context = context;
				this.requestId = requestId;
			}

			protected Bitmap doInBackground(String... params) {
				String name = params[0];
				Resources res = this.context.getResources();
				int id = res.getIdentifier(name, "drawable", context.getPackageName());

				if (id > 0) {
					BitmapDrawable result = (BitmapDrawable) res.getDrawable(id);
					return result.getBitmap();
				}

				return null;
			}

			protected void onPostExecute(final Bitmap result) {
				this.callback.onComplete(result, this.requestId);
			}
		}

		static class LoadImageFromFileTask extends AsyncTask<String, Void, Bitmap> {
			private CompleteCallback callback;
			private int requestId;

			public LoadImageFromFileTask(int requestId, CompleteCallback callback) {
				this.callback = callback;
				this.requestId = requestId;
			}

			protected Bitmap doInBackground(String... params) {
				String fileName = params[0];
				return BitmapFactory.decodeFile(fileName);
			}

			protected void onPostExecute(final Bitmap result) {
				this.callback.onComplete(result, this.requestId);
			}
		}

		static class LoadImageFromBase64StringTask extends AsyncTask<String, Void, Bitmap> {
			private CompleteCallback callback;
			private int requestId;

			public LoadImageFromBase64StringTask(int requestId, CompleteCallback callback) {
				this.callback = callback;
				this.requestId = requestId;
			}

			protected Bitmap doInBackground(String... params) {
				String source = params[0];
				byte[] bytes = Base64.decode(source, Base64.DEFAULT);
				return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
			}

			protected void onPostExecute(final Bitmap result) {
				this.callback.onComplete(result, this.requestId);
			}
		}
	}

	public static class Http
	{
		private static final String DELETE_METHOD = "DELETE";
		private static final String GET_METHOD = "GET";
		private static final String HEAD_METHOD = "HEAD";
		private static CookieManager cookieManager;

		public static class KeyValuePair
		{
			public String key;
			public String value;

			public KeyValuePair(String key, String value)
			{
				this.key = key;
				this.value = value;
			}
		}

		public static class RequestOptions
		{
			public String url;
			public String method;
			public ArrayList<KeyValuePair> headers;
			public String content;
			public int timeout = -1;
			public int screenWidth = -1;
			public int screenHeight = -1;

			public void addHeaders(HttpURLConnection connection)
			{
				if (this.headers == null)
				{
					return;
				}

				for (KeyValuePair pair : this.headers)
				{
					connection.addRequestProperty(pair.key.toString(), pair.value.toString());
				}
			}

			public void writeContent(HttpURLConnection connection, Stack<Closeable> openedStreams) throws IOException
			{
				if (this.content == null || this.content.getClass() != String.class)
				{
					return;
				}

				OutputStream outStream = connection.getOutputStream();
				openedStreams.push(outStream);

				OutputStreamWriter writer = new OutputStreamWriter(outStream);
				openedStreams.push(writer);

				writer.write((String) this.content);
			}
		}

		public static class RequestResult
		{
			public static final class ByteArrayOutputStream2 extends ByteArrayOutputStream
			{
				public ByteArrayOutputStream2()
				{
					super();
				}

				public ByteArrayOutputStream2(int size)
				{
					super(size);
				}

				/** Returns the internal buffer of this ByteArrayOutputStream, without copying. */
				public synchronized byte[] buf()
				{
					return this.buf;
				}
			}

			public ByteArrayOutputStream raw;
			public ArrayList<KeyValuePair> headers = new ArrayList<KeyValuePair>();
			public int statusCode;
			public String responseAsString;
			public Bitmap responseAsImage;
			public Exception error;

			public void getHeaders(HttpURLConnection connection)
			{
				Map<String, List<String>> headers = connection.getHeaderFields();
				if (headers == null)
				{
					// no headers, this may happen if there is no internet connection currently available
					return;
				}

				int size = headers.size();
				if (size == 0)
				{
					return;
				}

				for (Map.Entry<String, List<String>> entry: headers.entrySet()) {
					String key = entry.getKey();
					for (String value: entry.getValue()) {
						this.headers.add(new KeyValuePair(key, value));
					}
				}
			}

			public void readResponseStream(HttpURLConnection connection, Stack<Closeable> openedStreams, RequestOptions options) throws IOException
			{
				this.statusCode = connection.getResponseCode();

				int contentLength = connection.getContentLength();

				InputStream inStream;
				if (this.statusCode >= 400)
				{
					inStream = connection.getErrorStream();
				}
				else
				{
					inStream = connection.getInputStream();
				}

				if (inStream == null)
				{
					// inStream is null when receiving status code 401 or 407
					// see this thread for more information http://stackoverflow.com/a/24986433
					return;
				}

				openedStreams.push(inStream);

				BufferedInputStream buffer = new BufferedInputStream(inStream, 4096);
				openedStreams.push(buffer);

				ByteArrayOutputStream2 responseStream = contentLength != -1 ? new ByteArrayOutputStream2(contentLength) : new ByteArrayOutputStream2();
				openedStreams.push(responseStream);

				byte[] buff = new byte[4096];
				int read = -1;
				while ((read = buffer.read(buff, 0, buff.length)) != -1)
				{
					responseStream.write(buff, 0, read);
				}

				this.raw = responseStream;
				buff = null;

				// make the byte array conversion here, not in the JavaScript
				// world for better performance
				// since we do not have some explicit way to determine whether
				// the content-type is image
				try
				{
					// TODO: Generally this approach will not work for very
					// large files
					BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
					bitmapOptions.inJustDecodeBounds = true;

					// check the size of the bitmap first
					BitmapFactory.decodeByteArray(responseStream.buf(), 0, responseStream.size(), bitmapOptions);
					if (bitmapOptions.outWidth > 0 && bitmapOptions.outHeight > 0)
					{
						int scale = 1;
						final int height = bitmapOptions.outHeight;
						final int width = bitmapOptions.outWidth;

						if ((options.screenWidth > 0 && bitmapOptions.outWidth > options.screenWidth) ||
								(options.screenHeight > 0 && bitmapOptions.outHeight > options.screenHeight))
						{
							final int halfHeight = height / 2;
							final int halfWidth = width / 2;

							// scale down the image since it is larger than the
							// screen resolution
							while ((halfWidth / scale) > options.screenWidth && (halfHeight / scale) > options.screenHeight)
							{
								scale *= 2;
							}
						}

						bitmapOptions.inJustDecodeBounds = false;
						bitmapOptions.inSampleSize = scale;
						this.responseAsImage = BitmapFactory.decodeByteArray(responseStream.buf(), 0, responseStream.size(), bitmapOptions);
					}
				}
				catch (Exception e)
				{
					// bitmap decoding failed, the stream is not an image
					e.printStackTrace();
				}

				if (this.responseAsImage == null)
				{
					// convert to string
					this.responseAsString = responseStream.toString();
				}
			}
		}

		public static void MakeRequest(RequestOptions options, CompleteCallback callback, Object context)
		{
			if (cookieManager == null)
			{
				cookieManager = new CookieManager();
				CookieHandler.setDefault(cookieManager);
			}

			new HttpRequestTask(callback, context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, options);
		}

		static class HttpRequestTask extends AsyncTask<RequestOptions, Void, RequestResult>
		{
			private CompleteCallback callback;
			private Object context;

			public HttpRequestTask(CompleteCallback callback, Object context)
			{
				this.callback = callback;
				this.context = context;
			}

			@Override
			protected RequestResult doInBackground(RequestOptions... params)
			{
				RequestResult result = new RequestResult();
				Stack<Closeable> openedStreams = new Stack<Closeable>();

				try
				{
					RequestOptions options = params[0];
					URL url = new URL(options.url);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();

					// set the request method
					String requestMethod = options.method != null ? options.method.toUpperCase(Locale.ENGLISH) : GET_METHOD;
					connection.setRequestMethod(requestMethod);

					// add the headers
					options.addHeaders(connection);

					// apply timeout
					if (options.timeout > 0)
					{
						connection.setConnectTimeout(options.timeout);
					}

					// Do not attempt to write the content (body) for DELETE method, Java will throw directly
					if (!requestMethod.equals(DELETE_METHOD))
					{
						options.writeContent(connection, openedStreams);
					}

					// close the opened streams (saves copy-paste implementation
					// in each method that throws IOException)
					this.closeOpenedStreams(openedStreams);

					connection.connect();

					// build the result
					result.getHeaders(connection);
					if (!requestMethod.equals(HEAD_METHOD))
					{
						result.readResponseStream(connection, openedStreams, options);
					}

					// close the opened streams (saves copy-paste implementation
					// in each method that throws IOException)
					this.closeOpenedStreams(openedStreams);

					connection.disconnect();

					return result;
				}
				catch (Exception e) // TODO: Catch all exceptions?
				{
					e.printStackTrace();
					result.error = e;

					return result;
				}
				finally
				{
					try
					{
						this.closeOpenedStreams(openedStreams);
					}
					catch (IOException e)
					{
						e.printStackTrace();
						// TODO: Java rules - what to do here???
					}
				}
			}

			protected void onPostExecute(final RequestResult result)
			{
				this.callback.onComplete(result, this.context);
			}

			private void closeOpenedStreams(Stack<Closeable> streams) throws IOException
			{
				while (streams.size() > 0)
				{
					Closeable stream = streams.pop();
					stream.close();
				}
			}
		}
	}
}