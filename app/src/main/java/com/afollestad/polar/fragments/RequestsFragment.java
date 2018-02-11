package com.afollestad.polar.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import com.afollestad.dragselectrecyclerview.DragSelectRecyclerView;
import com.afollestad.dragselectrecyclerview.DragSelectRecyclerViewAdapter;
import com.afollestad.iconrequest.AppModel;
import com.afollestad.iconrequest.PolarConfig;
import com.afollestad.iconrequest.PolarRequest;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.util.DialogUtils;
import com.afollestad.polar.BuildConfig;
import com.afollestad.polar.R;
import com.afollestad.polar.adapters.RequestsAdapter;
import com.afollestad.polar.config.Config;
import com.afollestad.polar.fragments.base.BasePageFragment;
import com.afollestad.polar.ui.MainActivity;
import com.afollestad.polar.util.RequestLimiter;
import com.afollestad.polar.util.TintUtils;
import com.afollestad.polar.util.Utils;
import com.afollestad.polar.views.DisableableViewPager;
import io.reactivex.disposables.CompositeDisposable;
import java.io.File;
import java.util.List;

public class RequestsFragment extends BasePageFragment
    implements DragSelectRecyclerViewAdapter.SelectionListener,
        RequestsAdapter.SelectionChangedListener {

  private static final Object LOCK = new Object();

  @BindView(android.R.id.list)
  DragSelectRecyclerView list;

  @BindView(android.R.id.progress)
  View progress;

  @BindView(R.id.progressText)
  TextView progressText;

  @BindView(android.R.id.empty)
  TextView emptyText;

  @BindView(R.id.fab)
  FloatingActionButton fab;

  private DisableableViewPager mPager;
  private RequestsAdapter mAdapter;
  private MaterialDialog mDialog;
  private int mInitialSelection = -1;
  private boolean mAppsLoaded = false;
  private boolean mIsLoading = false;
  private PolarRequest request;
  private CompositeDisposable subs;

  private final Runnable mInvalidateLimitRunnable =
      new Runnable() {
        @Override
        public void run() {
          final Activity act = getActivity();
          if (!isAdded() || act == null || act.isFinishing()) {
            return;
          }
          mAdapter.invalidateAllowRequest(act);
          if (!isAdded() || act.isFinishing()) {
            return;
          }
          long nextCheck = RequestLimiter.get(act).intervalMs();
          if (nextCheck < (1000 * 60 * 60)) {
            nextCheck = 1000; // if less than a hour, update every second.
          }
          mHandler.postDelayed(this, nextCheck);
        }
      };
  private Handler mHandler;
  private Unbinder unbinder;

  public RequestsFragment() {}

  @Override
  public int getTitle() {
    return R.string.request_icons;
  }

  public boolean onBackPressed() {
    if (mAdapter != null) {
      if (mAdapter.getSelectedCount() > 0) {
        if (request != null) {
          request.deselectAll();
          mAdapter.clearSelected();
        }
        mAdapter.notifyDataSetChanged();
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public void updateTitle() {
    synchronized (LOCK) {
      MainActivity act = (MainActivity) getActivity();
      if (act != null) {
        if (fab == null) {
          act.setTitle(R.string.request_icons);
          return;
        }

        final int numSelected = mAdapter != null ? mAdapter.getSelectedCount() : 0;
        if (numSelected == 0) {
          act.setTitle(R.string.request_icons);
        } else {
          act.setTitle(getString(R.string.request_icons_x, numSelected));
        }

        if (!fab.isShown() && numSelected > 0) {
          fab.show();
        } else if (fab.isShown() && numSelected == 0) {
          fab.hide();
        }
        // Work around for the icon sometimes being invisible?
        fab.setImageResource(R.drawable.ic_action_apply);
        // Update toolbar items
        //invalidateOptionsMenu();
      }
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_requesticons, container, false);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.cab_requests, menu);
    super.onCreateOptionsMenu(menu, inflater);
    synchronized (LOCK) {
      MenuItem selectAll = menu.findItem(R.id.selectAll);
      try {
        final Activity act = getActivity();
        final int tintColor = DialogUtils.resolveColor(act, R.attr.toolbar_icons_color);
        if (mAdapter == null || mAdapter.getSelectedCount() == 0) {
          selectAll.setIcon(
              TintUtils.createTintedDrawable(act, R.drawable.ic_action_selectall, tintColor));
        } else {
          selectAll.setIcon(
              TintUtils.createTintedDrawable(act, R.drawable.ic_action_close, tintColor));
        }
      } catch (Throwable e) {
        e.printStackTrace();
        selectAll.setVisible(false);
      }
      selectAll.setVisible(mAppsLoaded);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    synchronized (LOCK) {
      if (item.getItemId() == R.id.selectAll) {
        if (request != null) {
          if (mAdapter.getSelectedCount() == 0) {
            request.selectAll();
            for (int i = 0; i < mAdapter.getItemCount(); i++) {
              mAdapter.setSelected(i, true);
            }
          } else {
            request.deselectAll();
            mAdapter.clearSelected();
          }
          mAdapter.notifyDataSetChanged();
        }
        return true;
      }
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    unbinder = ButterKnife.bind(this, view);

    GridLayoutManager lm = new GridLayoutManager(getActivity(), Config.get().gridWidthRequests());
    lm.setSpanSizeLookup(
        new GridLayoutManager.SpanSizeLookup() {
          @Override
          public int getSpanSize(int position) {
            if (position == 0) {
              return Config.get().gridWidthRequests();
            }
            return 1;
          }
        });

    mAdapter = new RequestsAdapter(getActivity(), this);
    mAdapter.setSelectionListener(this);
    mAdapter.setMaxSelectionCount(Config.get().iconRequestMaxCount());

    list.setLayoutManager(lm);
    list.setAdapter(mAdapter);

    emptyText.setText(R.string.no_apps);
    emptyText.setVisibility(View.GONE);
    progress.setVisibility(View.VISIBLE);
    progressText.setVisibility(View.VISIBLE);
    progressText.setText(R.string.loading_filter);
    list.setVisibility(View.GONE);

    mPager = getActivity().findViewById(R.id.pager);
    if (!Config.get().navDrawerModeEnabled()) {
      // Swiping is only enabled in nav drawer mode, so no need to run this code in nav drawer mode
      list.setFingerListener(dragActive -> mPager.setPagingEnabled(!dragActive));
    }

    emptyText.setOnClickListener(view1 -> onClickFab());

    subs = new CompositeDisposable();
    request =
        PolarRequest.make(getActivity(), savedInstanceState)
            .uriTransformer(
                uri ->
                    FileProvider.getUriForFile(
                        getActivity(),
                        BuildConfig.APPLICATION_ID + ".fileProvider",
                        new File(uri.getPath())));
    setRequestListeners();

    if (!request.getLoadedApps().isEmpty()) {
      mAdapter.setApps(request.getLoadedApps());
      mAdapter.restoreInstanceState(savedInstanceState);
      if (!mIsLoading && mAdapter.getItemCount() > 0) {
        emptyText.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
      }
    }
  }

  private void setRequestListeners() {
    subs.add(
        request
            .loading()
            .subscribe(
                isLoading -> {
                  if (isLoading) {
                    mAppsLoaded = false;
                    showProgress(R.string.loading_filter);
                  }
                }));
    subs.add(
        request
            .selectionChange()
            .flatMap(appModel -> request.getSelectedApps().toObservable())
            .subscribe(
                appModels -> {
                  if (appModels.size() == 0) {
                    fab.hide();
                  } else {
                    fab.show();
                  }
                }));
    subs.add(
        request
            .sending()
            .subscribe(
                isSending -> {
                  if (isSending) {
                    mDialog =
                        new MaterialDialog.Builder(getActivity())
                            .content(R.string.preparing_icon_request)
                            .progress(true, -1)
                            .cancelable(false)
                            .show();
                  }
                }));
    subs.add(
        request
            .sent()
            .subscribe(
                sendResult -> {
                  if (sendResult.success()) {
                    onRequestSent();
                  } else {
                    mDialog.dismiss();
                    Utils.showError(getActivity(), sendResult.error());
                  }
                }));
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    unbinder.unbind();
  }

  @Override
  public void onResume() {
    super.onResume();
    reload();
    if (getActivity() != null) {
      ((MainActivity) getActivity()).showChangelogIfNecessary();
    }
    if (RequestLimiter.needed(getActivity())) {
      if (mHandler == null) {
        mHandler = new Handler();
      }
      mHandler.post(mInvalidateLimitRunnable);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mAdapter.saveInstanceState(outState);
    request.saveInstance(outState);
  }

  private PolarConfig getPolarConfig() {
    PolarConfig.Builder configBuilder =
        PolarConfig.create(getActivity())
            .emailRecipient(getString(R.string.icon_request_email))
            .emailSubject(
                String.format(
                    "%s %s", getString(R.string.app_name), getString(R.string.icon_request)))
            .emailFooter(
                getString(
                    R.string.x_version_x,
                    getString(R.string.app_name),
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE))
            .includeDeviceInfo(true);
    String remoteHost = Config.get().polarBackendHost();
    if (remoteHost != null && !remoteHost.trim().isEmpty()) {
      configBuilder.apiHost(remoteHost).apiKey(Config.get().polarBackendApiKey());
    }
    return configBuilder.build();
  }

  @SuppressLint("StringFormatInvalid")
  private void reload() {
    synchronized (LOCK) {
      if (mIsLoading) {
        return;
      }
      mIsLoading = true;

      if (request.getLoadedApps().isEmpty()) {
        showProgress(0);
        subs.add(
            request
                .load()
                .subscribe(loadResult -> onAppsLoaded(loadResult.apps(), loadResult.error())));
      } else {
        onAppsLoaded(request.getLoadedApps(), null);
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    synchronized (LOCK) {
      request = null;
      if (subs != null) {
        subs.dispose();
      }
      if (mDialog != null) {
        mDialog.dismiss();
      }
    }
    if (mHandler != null) {
      mHandler.removeCallbacks(mInvalidateLimitRunnable);
      mHandler = null;
    }
  }

  // Icon Requests

  private void showProgress(@StringRes int text) {
    emptyText.setVisibility(View.VISIBLE);
    emptyText.setVisibility(View.GONE);
    progress.setVisibility(View.VISIBLE);
    list.setVisibility(View.GONE);
    if (text == 0) {
      text = R.string.please_wait;
    }
    progressText.setText(text);
  }

  public void onAppsLoaded(List<AppModel> arrayList, Exception e) {
    synchronized (LOCK) {
      mIsLoading = false;
      if (progressText == null || request == null) {
        return;
      }
      emptyText.setVisibility(arrayList == null || arrayList.isEmpty() ? View.VISIBLE : View.GONE);
      mAppsLoaded = true;
      if (request == null) {
        return;
      }
      getActivity().invalidateOptionsMenu();
      mAdapter.setApps(arrayList);
      mAdapter.notifyDataSetChanged();
      emptyText.setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
      progress.setVisibility(View.GONE);
      list.setVisibility(mAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
    }
  }

  public void onRequestSent() {
    if (getActivity() == null) {
      return;
    }
    final Activity act = getActivity();
    subs.add(
        request
            .getSelectedApps()
            .subscribe((appModels, throwable) -> RequestLimiter.get(act).update(appModels.size())));
    if (RequestLimiter.needed(getActivity())) {
      if (mHandler != null) {
        mHandler.removeCallbacks(mInvalidateLimitRunnable);
      } else {
        mHandler = new Handler();
      }
      mHandler.post(mInvalidateLimitRunnable);
    }
    mDialog.dismiss();
    fab.hide();
    request.deselectAll();
    mAdapter.clearSelected();
    mAdapter.notifyDataSetChanged();

    final String backendHost = Config.get().polarBackendHost();
    if (backendHost != null && !backendHost.trim().isEmpty()) {
      Toast.makeText(getActivity(), R.string.request_uploaded, Toast.LENGTH_LONG).show();
    }
  }

  @OnClick(R.id.fab)
  public void onClickFab() {
    if (!Config.get().iconRequestEnabled()) {
      Utils.showError(
          getActivity(),
          new Exception("The developer has not set an email for icon requests yet."));
      return;
    }

    synchronized (LOCK) {
      if (getActivity() == null || request == null) {
        return;
      }
      final List<AppModel> apps = request.getLoadedApps();
      for (int i = 0; i < apps.size(); i++) {
        if (mAdapter.isIndexSelected(i + 1)) {
          request.select(apps.get(i));
        } else {
          request.deselect(apps.get(i));
        }
      }
      subs.add(request.send().subscribe());
    }
  }

  @Override
  public void onDragSelectionChanged(int count) {
    updateTitle();
    getActivity().invalidateOptionsMenu();
  }

  @Override
  public void onClick(int index, boolean longClick) {
    if (longClick) {
      if (mAdapter.isIndexSelected(index)) {
        return;
      }
      mInitialSelection = index;
      list.setDragSelectActive(true, index);
    } else {
      if (index == mInitialSelection) {
        list.setDragSelectActive(false, -1);
        mInitialSelection = -1;
      }
      mAdapter.toggleSelected(index);
    }
  }
}
