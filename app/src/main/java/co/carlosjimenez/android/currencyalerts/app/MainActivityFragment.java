/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Carlos Andres Jimenez <apps@carlosandresjimenez.co>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package co.carlosjimenez.android.currencyalerts.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MarginLayoutParamsCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import butterknife.BindView;
import butterknife.ButterKnife;
import co.carlosjimenez.android.currencyalerts.app.data.Currency;
import co.carlosjimenez.android.currencyalerts.app.data.ForexContract;
import co.carlosjimenez.android.currencyalerts.app.sync.ForexSyncAdapter;
import co.carlosjimenez.android.currencyalerts.app.sync.LoadCurrencyTask;
import co.carlosjimenez.android.currencyalerts.app.widget.CurrencyEditText;

public class MainActivityFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String LOG_TAG = MainActivityFragment.class.getSimpleName();

    // These indices are tied to FOREX_COLUMNS.  If FOREX_COLUMNS changes, these
    // must change.
    static final int COL_RATE_ID = 0;
    static final int COL_CURRENCY_FROM_ID = 1;
    static final int COL_CURRENCY_FROM_NAME = 2;
    static final int COL_CURRENCY_FROM_SYMBOL = 3;
    static final int COL_COUNTRY_FROM_CODE = 4;
    static final int COL_COUNTRY_FROM_NAME = 5;
    static final int COL_COUNTRY_FROM_FLAG = 6;
    static final int COL_CURRENCY_TO_ID = 7;
    static final int COL_CURRENCY_TO_NAME = 8;
    static final int COL_CURRENCY_TO_SYMBOL = 9;
    static final int COL_COUNTRY_TO_CODE = 10;
    static final int COL_COUNTRY_TO_NAME = 11;
    static final int COL_COUNTRY_TO_FLAG = 12;
    static final int COL_RATE_DATE = 13;
    static final int COL_RATE_VAL = 14;

    private static final int FOREX_LOADER = 0;

    @BindView(R.id.currencyFromAmount)
    CurrencyEditText mCurrencyEditText;
    @BindView(R.id.clMain)
    CoordinatorLayout mCoordinatorLayout;
    @BindView(R.id.topPanel)
    RelativeLayout mRelativeLayout;
    @BindView(R.id.recyclerview_forex)
    RecyclerView mRecyclerView;
    @BindView(R.id.currencyFromDescription)
    TextView mCurrencyDescription;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.appbarLayout)
    AppBarLayout mAppBarLayout;
    @BindView(R.id.currencyFromFlag)
    ImageView mImageView;
    @BindView(R.id.recyclerview_forex_empty)
    TextView mTvEmptyView;
    @BindView(R.id.adView)
    AdView mAdView;

    private AppCompatActivity mContext;
    private FloatingActionButton mCalculateButton;
    private ForexAdapter mForexAdapter;
    private Currency mMainCurrency;
    private FirebaseAnalytics mFirebaseAnalytics;

    private final Handler mHandler = new Handler();
    private static final int AD_DELAY_MILLISECONDS = 1000;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            int message = intent.getIntExtra(ForexSyncAdapter.FOREX_DATA_STATUS, -1);

            if (message == ForexSyncAdapter.FOREX_STATUS_OK) {
                refreshForexList();
            }
        }
    };

    public MainActivityFragment() {
        setHasOptionsMenu(true);
    }

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callback {
        /**
         * MainActivityFragment Callback to perform an action on the activity when an item
         * has been selected.
         */
        void onItemSelected(Uri rateUri, ImageView imageFrom, ForexAdapter.ForexAdapterViewHolder vh);

        void onSettingsSelected();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCoordinatorLayout.removeView(mCalculateButton);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        ButterKnife.bind(this, rootView);

        mContext = (AppCompatActivity) getActivity();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(mContext);

        mContext.setSupportActionBar(mToolbar);
        mContext.getSupportActionBar().setDisplayShowHomeEnabled(true);
        mContext.getSupportActionBar().setDisplayShowTitleEnabled(true);

        mMainCurrency = Utility.getMainCurrency(mContext);

        // Set the layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // The ForecastAdapter will take data from a source and
        // use it to populate the RecyclerView it's attached to.
        mForexAdapter = new ForexAdapter(mContext, new ForexAdapter.ForexAdapterOnClickHandler() {
            @Override
            public void onClick(String currencyId, String currencyName, ForexAdapter.ForexAdapterViewHolder vh) {
                String[] currencies = {mMainCurrency.getId(), currencyId};

                sendHitToAnalytics(currencyId, currencyName);

                double value = Double.parseDouble(mCurrencyEditText.getText().toString());
                ((Callback) getActivity()).onItemSelected(
                        ForexContract.RateEntry.buildCurrencyRateWithValue(currencies, value),
                        mImageView,
                        vh);
            }
        }, mTvEmptyView);

        // specify an adapter (see also next example)
        mRecyclerView.setAdapter(mForexAdapter);

        getCalculateButton();
        loadMainCurrencyDetails();

        mCoordinatorLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft,
                                       int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);
                addFloatingActionButton();
            }
        });

        new LoadCurrencyTask(mContext).execute();

        ForexSyncAdapter.initializeSyncAdapter(mContext);

        // Ad is delayed some seconds as this affects the performance
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadAd();
            }
        }, AD_DELAY_MILLISECONDS);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(FOREX_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mRecyclerView) {
            mRecyclerView.clearOnScrollListeners();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mMessageReceiver,
                new IntentFilter(ForexSyncAdapter.ACTION_DATA_UPDATED));

    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mMessageReceiver);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (getActivity() instanceof MainActivity) {
            // Inflate the menu; this adds items to the action bar if it is present.
            inflater.inflate(R.menu.menu_main, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        switch (item.getItemId()) {
            case R.id.action_settings:
                ((Callback) getActivity()).onSettingsSelected();
                return true;
            case R.id.action_refresh:
                refresh();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        // Sort order:  Ascending, by date.
        Uri rateUri = ForexContract.RateEntry.buildStartCurrencyWithDate(
                mMainCurrency.getId(),
                Utility.getForexSyncDate(mContext));

        return new CursorLoader(getActivity(),
                rateUri,
                ForexContract.RATE_CURRENCY_COLUMNS,
                null,
                null,
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        String maxRateString = "";
        String rateString = "";
        String maxRateSymbol = "";
        double maxRateValue = 0;

        updateEmptyView();

        if (data == null) {
            Log.d(LOG_TAG, "Main Forex Loader Finished: No data returned");

            return;
        }
        if (data.getCount() <= 0) {
            Log.d(LOG_TAG, "Main Forex Loader Finished: No data returned");

            data.close();
            return;
        }

        for (int i = 0; i < data.getCount(); i++) {
            data.moveToPosition(i);
            rateString = Utility.formatCurrencyRate(getActivity(), data.getString(COL_CURRENCY_TO_SYMBOL), data.getDouble(COL_RATE_VAL));

            if (rateString.length() > maxRateString.length()) {
                maxRateString = rateString;
                maxRateSymbol = data.getString(COL_CURRENCY_TO_SYMBOL);
                maxRateValue = data.getDouble(COL_RATE_VAL);
            }
        }

        mForexAdapter.setMaxRateVal(maxRateSymbol, maxRateValue);
        mForexAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mForexAdapter.swapCursor(null);
    }

    public void refresh() {
        ForexSyncAdapter.syncImmediately(getActivity());
    }

    public void refreshForexList() {
        getLoaderManager().restartLoader(FOREX_LOADER, null, this);
    }

    /**
     * Helper method to obtain the main currency from the shared preferences and set the information
     * on the top frame.
     */
    public void loadMainCurrencyDetails() {
        mCurrencyEditText.setPrefix(mMainCurrency.getSymbol());
        mCurrencyEditText.setSelection(mCurrencyEditText.getText().length());
        mCurrencyEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_GO ||
                        actionId == EditorInfo.IME_ACTION_DONE) {
                    calculateRates();
                    handled = true;
                }
                return handled;
            }
        });

        Glide.with(this)
                .load(mMainCurrency.getCountryFlag())
                .error(R.drawable.globe)
                .centerCrop()
                .crossFade()
                .into(mImageView);
        mImageView.setContentDescription(Utility.formatCountryFlagName(mContext, mMainCurrency.getCountryName()));

        mCurrencyDescription.setText(mMainCurrency.getName());
        mImageView.setContentDescription(mMainCurrency.getName());
    }

    /**
     * Updates the empty list view with contextually relevant information that the user can
     * use to determine why they aren't seeing currency rates.
     */
    private void updateEmptyView() {
        if (mForexAdapter.getItemCount() == 0) {
            if (null != mTvEmptyView) {
                // if cursor is empty, why? do we have an invalid location
                int message = R.string.empty_forex_list;
                @ForexSyncAdapter.ForexStatus int status = Utility.getForexStatus(getActivity());
                switch (status) {
                    case ForexSyncAdapter.FOREX_STATUS_SERVER_DOWN:
                        message = R.string.empty_forex_list_server_down;
                        break;
                    case ForexSyncAdapter.FOREX_STATUS_SERVER_INVALID:
                        message = R.string.empty_forex_list_server_error;
                        break;
                    case ForexSyncAdapter.FOREX_STATUS_INVALID:
                        message = R.string.empty_forex_list_invalid;
                        break;
                    default:
                        if (!Utility.isNetworkAvailable(getActivity())) {
                            message = R.string.empty_forex_list_no_network;
                        }
                }
                mTvEmptyView.setText(message);
            }
        }
    }

    /**
     * Method to set the position of the FAB button according to the material design guidelines.
     */
    private void addFloatingActionButton() {
        final int fabSize = getResources().getDimensionPixelSize(R.dimen.size_fab);
        final int spacingDouble = getResources().getDimensionPixelSize(R.dimen.spacing_double);

        int bottomOfToolbar = mAppBarLayout.getBottom();

        final CoordinatorLayout.LayoutParams fabLayoutParams = new CoordinatorLayout.LayoutParams(fabSize, fabSize);
        fabLayoutParams.gravity = Gravity.END | Gravity.TOP;
        final int halfAFab = fabSize / 2;

        fabLayoutParams.setMargins(0, // left
                bottomOfToolbar - halfAFab, //top
                0, // right
                spacingDouble); // bottom
        MarginLayoutParamsCompat.setMarginEnd(fabLayoutParams, spacingDouble);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Account for the fab's emulated shadow.
            fabLayoutParams.topMargin -= (mCalculateButton.getPaddingTop() / 2);
        }
        mCoordinatorLayout.addView(mCalculateButton, fabLayoutParams);
    }

    /**
     * Method to create a new FAB button.
     */
    private void getCalculateButton() {
        if (null == mCalculateButton) {
            mCalculateButton = new FloatingActionButton(mContext);
            mCalculateButton.setImageDrawable(mContext.getDrawable(R.drawable.ic_calculator_grey600_24dp));
            mCalculateButton.setContentDescription(mContext.getString(R.string.calculate_description));
            mCalculateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFabClicked();
                }
            });
        }
    }

    public void onFabClicked() {
        calculateRates();
    }

    /**
     * Method that handles the calculation of the rate when the user click on the FAB or the
     * keyboard go buttons.
     */
    public void calculateRates() {
        if (mForexAdapter == null || mForexAdapter.getItemCount() == 0) {
            showErrorMessage(getString(R.string.empty_forex_list));
            return;
        }

        if (mCurrencyEditText == null || mCurrencyEditText.getText().length() <= 0) {
            showAlertMessage();
            return;
        }

        mForexAdapter.setMainAmount(Double.parseDouble(mCurrencyEditText.getText().toString()));
        hideIme();
    }

    /**
     * Helper method to show errors on a snack bar.
     *
     * @param message message to show to the user
     */
    private void showErrorMessage(String message) {
        Snackbar.make(mCoordinatorLayout, message, Snackbar.LENGTH_LONG)
                .setAction("Refresh", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        refresh();
                    }
                }).show();
    }

    /**
     * Helper method to show errors on an alert dialog.
     */
    private void showAlertMessage() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);

        // set dialog message
        alertDialogBuilder
                .setMessage(mContext.getString(R.string.dialog_calc_rates_empty_value))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {

                    }
                });
        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    /**
     * This method forces the soft keyboard to hide
     */
    private void hideIme() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Activity.INPUT_METHOD_SERVICE);
        //Find the currently focused view, so we can grab the correct window token from it.
        View view = mContext.getCurrentFocus();
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = new View(mContext);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * Helper method to load a Google Ad
     */
    private void loadAd() {
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        mAdView.loadAd(adRequest);
    }

    /**
     * Helper method to send the hit to Google Analytics of a selected currency to be
     * displayed on the detail screen
     *
     * @param currencyId       String containing the currency id
     * @param currencyName     String containing the currency name
     */
    private void sendHitToAnalytics(String currencyId, String currencyName) {
        Bundle payload = new Bundle();
        payload.putString(FirebaseAnalytics.Param.ITEM_ID, currencyId);
        payload.putString(FirebaseAnalytics.Param.ITEM_NAME, currencyName);
        payload.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "text/html");
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, payload);
    }
}
