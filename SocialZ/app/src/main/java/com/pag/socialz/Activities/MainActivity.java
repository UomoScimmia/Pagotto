package com.pag.socialz.Activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.pag.socialz.Adapters.PostAdapter;
import com.pag.socialz.Enums.PostStatus;
import com.pag.socialz.Enums.ProfileStatus;
import com.pag.socialz.Listeners.OnObjectExistListener;
import com.pag.socialz.Managers.AnimationManager;
import com.pag.socialz.Managers.DatabaseManager;
import com.pag.socialz.Managers.PostManager;
import com.pag.socialz.Managers.ProfileManager;
import com.pag.socialz.Models.Post;
import com.pag.socialz.R;

public class MainActivity extends BaseActivity{


    private static final String TAG = MainActivity.class.getSimpleName();

    private PostAdapter postsAdapter;
    private RecyclerView recyclerView;
    private FloatingActionButton floatingActionButton;

    private ProfileManager profileManager;
    private PostManager postManager;
    private int counter;
    private TextView newPostsCounterTextView;
    private PostManager.PostCounterWatcher postCounterWatcher;
    private boolean counterAnimationInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        profileManager = ProfileManager.getInstance(this);
        postManager = PostManager.getInstance(this);
        initContentView();

        postCounterWatcher = new PostManager.PostCounterWatcher() {
            @Override
            public void onPostCounterChanged(int newValue) {
                updateNewPostCounter();
            }
        };

        postManager.setPostCounterWatcher(postCounterWatcher);

        setOnLikeAddedListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNewPostCounter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ProfileActivity.CREATE_POST_FROM_PROFILE_REQUEST:
                    refreshPostList();
                    break;
                case CreatePostActivity.CREATE_NEW_POST_REQUEST:
                    refreshPostList();
                    showFloatButtonRelatedSnackBar(R.string.message_post_was_created);
                    break;

                case PostDetailsActivity.UPDATE_POST_REQUEST:
                    if (data != null) {
                        PostStatus postStatus = (PostStatus) data.getSerializableExtra(PostDetailsActivity.POST_STATUS_EXTRA_KEY);
                        if (postStatus.equals(PostStatus.REMOVED)) {
                            postsAdapter.removeSelectedPost();
                            showFloatButtonRelatedSnackBar(R.string.message_post_was_removed);
                        } else if (postStatus.equals(PostStatus.UPDATED)) {
                            postsAdapter.updateSelectedPost();
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        //inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                ProfileStatus profileStatus = profileManager.checkProfile();

                if (profileStatus.equals(ProfileStatus.PROFILE_CREATED)) {
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    openProfileActivity(userId);
                } else {
                    doAuthorization(profileStatus);
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initContentView() {
        if (recyclerView == null) {
            floatingActionButton = findViewById(R.id.addNewPostFab);

            if (floatingActionButton != null) {
                floatingActionButton.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        if (hasInternetConnection()) {
                            addPostClickAction();
                        } else {
                            showFloatButtonRelatedSnackBar(R.string.internet_connection_failed);
                        }
                    }
                });
            }
            newPostsCounterTextView = findViewById(R.id.newPostsCounterTextView);
            newPostsCounterTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refreshPostList();
                }
            });

            final ProgressBar progressBar =  findViewById(R.id.progressBar);
            SwipeRefreshLayout swipeContainer = findViewById(R.id.swipeContainer);
            recyclerView = findViewById(R.id.recycler_view);
            postsAdapter = new PostAdapter(this, swipeContainer);
            postsAdapter.setCallback(new PostAdapter.Callback() {
                @Override
                public void onItemClick(final Post post, final View view) {
                    PostManager.getInstance(MainActivity.this).isPostExistSingleValue(post.getId(), new OnObjectExistListener<Post>() {
                        @Override
                        public void onDataChanged(boolean exist) {
                            if (exist) {
                                openPostDetailsActivity(post, view);
                            } else {
                                showFloatButtonRelatedSnackBar(R.string.error_post_was_removed);
                            }
                        }
                    });
                }

                @Override
                public void onListLoadingFinished() {
                    progressBar.setVisibility(View.GONE);
                }

                @Override
                public void onAuthorClick(String authorId, View view) {
                    //openProfileActivity(authorId, view);
                }

                @Override
                public void onCanceled(String message) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
            recyclerView.setAdapter(postsAdapter);
            postsAdapter.loadFirstPage();
            updateNewPostCounter();

            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    hideCounterView();
                    super.onScrolled(recyclerView, dx, dy);
                }
            });
        }
    }

    private void setOnLikeAddedListener() {
        DatabaseManager.getInstance(this).onNewLikeAddedListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                counter++;
                showSnackBar("You have " + counter + " new likes");
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void updateNewPostCounter() {
        Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                int newPostsQuantity = postManager.getNewPostsCounter();

                if (newPostsCounterTextView != null) {
                    if (newPostsQuantity > 0) {
                        showCounterView();

                        String counterFormat = getResources().getQuantityString(R.plurals.new_posts_counter_format, newPostsQuantity, newPostsQuantity);
                        newPostsCounterTextView.setText(String.format(counterFormat, newPostsQuantity));
                    } else {
                        hideCounterView();
                    }
                }
            }
        });
    }

    private void showCounterView() {
        AnimationManager.showViewByScaleAndVisibility(newPostsCounterTextView);
    }

    private void hideCounterView() {
        if (!counterAnimationInProgress && newPostsCounterTextView.getVisibility() == View.VISIBLE) {
            counterAnimationInProgress = true;
            AlphaAnimation alphaAnimation = AnimationManager.hideViewByAlpha(newPostsCounterTextView);
            alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    counterAnimationInProgress = false;
                    newPostsCounterTextView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            alphaAnimation.start();
        }
    }

    public void showFloatButtonRelatedSnackBar(int messageId) {
        showSnackBar(floatingActionButton, messageId);
    }

    private void addPostClickAction() {
        ProfileStatus profileStatus = profileManager.checkProfile();

        if (profileStatus.equals(ProfileStatus.PROFILE_CREATED)) {
            openCreatePostActivity();
        } else {
            doAuthorization(profileStatus);
        }
    }

    private void refreshPostList() {
        postsAdapter.loadFirstPage();
        if (postsAdapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(0);
        }
    }

    private void openPostDetailsActivity(Post post, View v) {
        Intent intent = new Intent(MainActivity.this, PostDetailsActivity.class);
        intent.putExtra(PostDetailsActivity.POST_ID_EXTRA_KEY, post.getId());
        startActivityForResult(intent, PostDetailsActivity.UPDATE_POST_REQUEST);
    }

    private void openCreatePostActivity() {
        Intent intent = new Intent(this, CreatePostActivity.class);
        startActivityForResult(intent, CreatePostActivity.CREATE_NEW_POST_REQUEST);
    }

    private void openProfileActivity(String userId) {
        openProfileActivity(userId, null);
    }

    @SuppressLint("RestrictedApi")
    private void openProfileActivity(String userId, View view) {
        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.USER_ID_EXTRA_KEY, userId);
        startActivityForResult(intent, ProfileActivity.CREATE_POST_FROM_PROFILE_REQUEST);
    }
}
