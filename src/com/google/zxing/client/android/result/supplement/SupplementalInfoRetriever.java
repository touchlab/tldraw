/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android.result.supplement;

import android.content.Context;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.TextView;
import com.google.zxing.client.result.ISBNParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ProductParsedResult;
import com.google.zxing.client.result.URIParsedResult;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

public abstract class SupplementalInfoRetriever implements Callable<Void> {

  private static ExecutorService executorInstance = null;

  private static synchronized ExecutorService getExecutorService() {
    if (executorInstance == null) {
      executorInstance = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          t.setDaemon(true);
          return t;
        }
      });
    }
    return executorInstance;
  }

  public static void maybeInvokeRetrieval(TextView textView,
                                          ParsedResult result,
                                          Handler handler,
                                          Context context) {

    Collection<SupplementalInfoRetriever> retrievers = new ArrayList<SupplementalInfoRetriever>(1);

    if (result instanceof URIParsedResult) {
      retrievers.add(new URIResultInfoRetriever(textView, (URIParsedResult) result, handler, context));
    } else if (result instanceof ProductParsedResult) {
      String productID = ((ProductParsedResult) result).getProductID();
      retrievers.add(new ProductResultInfoRetriever(textView, productID, handler, context));
    } else if (result instanceof ISBNParsedResult) {
      String isbn = ((ISBNParsedResult) result).getISBN();
      retrievers.add(new ProductResultInfoRetriever(textView, isbn, handler, context));
      retrievers.add(new BookResultInfoRetriever(textView, isbn, handler, context));
    }

    for (SupplementalInfoRetriever retriever : retrievers) {
      ExecutorService executor = getExecutorService();
      Future<?> future = executor.submit(retriever);
      // Make sure it's interrupted after a short time though
      executor.submit(new KillerCallable(future, 10, TimeUnit.SECONDS));
    }
  }

  private final WeakReference<TextView> textViewRef;
  private final Handler handler;

  SupplementalInfoRetriever(TextView textView, Handler handler) {
    this.textViewRef = new WeakReference<TextView>(textView);
    this.handler = handler;
  }

  @Override
  public final Void call() throws IOException, InterruptedException {
    retrieveSupplementalInfo();
    return null;
  }

  abstract void retrieveSupplementalInfo() throws IOException, InterruptedException;

  final void append(String itemID, String source, String[] newTexts, String linkURL) throws InterruptedException {

    final TextView textView = textViewRef.get();
    if (textView == null) {
      throw new InterruptedException();
    }

    StringBuilder newTextCombined = new StringBuilder();

    if (source != null) {
      newTextCombined.append(source).append(" : ");
    }

    int linkStart = newTextCombined.length();

    boolean first = true;
    for (String newText : newTexts) {
      if (first) {
        newTextCombined.append(newText);
        first = false;
      } else {
        newTextCombined.append(" [");
        newTextCombined.append(newText);
        newTextCombined.append(']');
      }
    }

    int linkEnd = newTextCombined.length();

    String newText = newTextCombined.toString();
    final Spannable content = new SpannableString(newText + "\n\n");
    if (linkURL != null) {
      content.setSpan(new URLSpan(linkURL), linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    handler.post(new Runnable() {
      @Override
      public void run() {
        textView.append(content);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
      }
    });

  }

}
