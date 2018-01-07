package com.afollestad.polar.adapters;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.afollestad.polar.R;
import com.afollestad.polar.util.DrawableXmlParser;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class IconMoreAdapter extends RecyclerView.Adapter<IconMoreAdapter.MainViewHolder> {

  private final Context mContext;
  private final ClickListener mListener;
  private final int mIconsInAnimation;
  private final List<DrawableXmlParser.Icon> mIcons;

  public IconMoreAdapter(ClickListener listener, int gridWidth, Context context) {
    mListener = listener;
    mContext = context;
    mIcons = new ArrayList<>();
    mIconsInAnimation = gridWidth * 2;

    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return mIcons.get(position).getUniqueId();
  }

  public void set(List<DrawableXmlParser.Icon> icons) {
    mIcons.clear();
    mIcons.addAll(icons);
    notifyDataSetChanged();
  }

  public DrawableXmlParser.Icon getIcon(int index) {
    return mIcons.get(index);
  }

  @Override
  public int getItemCount() {
    return mIcons.size();
  }

  @Override
  public MainViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View v =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_icon, parent, false);
    return new MainViewHolder(v);
  }

  @Override
  public void onBindViewHolder(final MainViewHolder holder, int position) {
    final Context c = holder.itemView.getContext();
    final int res = mIcons.get(position).getDrawableId(c);

    if (position < mIconsInAnimation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      String transitionName =
          mContext.getString(R.string.transition_name_recyclerview_item) + position;
      holder.itemView.setTransitionName(transitionName);
    }

    holder.image.setBackground(null);
    holder.image.setImageDrawable(null);

    if (res == 0) {
      holder.image.setBackgroundColor(Color.parseColor("#40000000"));
    } else {
      Glide.with(c).load(res).into(holder.image);
    }
  }

  public interface ClickListener {

    void onClick(View view, int index);
  }

  class MainViewHolder extends RecyclerView.ViewHolder {

    final ImageView image;

    MainViewHolder(View itemView) {
      super(itemView);
      image = itemView.findViewById(R.id.image);
      itemView.setOnClickListener(v -> mListener.onClick(v, getAdapterPosition()));
    }
  }
}
