<template>
  <section class="cloudnext-page drive-directory-page" :class="{ 'm-page-flush': true }">


      <div v-if="currentParentId" class="m-mp-breadcrumb">
        <button class="m-mp-back m-tap" type="button" @click="onMobileGoBack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          <span>上一级</span>
        </button>
        <span class="m-mp-current">{{ mobileCurrentFolderName }}</span>
      </div>
      <MobileFilterChips v-model="activeFilter" :chips="filters"
        :sort-by="sortBy" :sort-order="sortOrder"
        @sort-change="({ sortBy: key, sortOrder: order }) => setSort(key, order)"
        @update:modelValue="applyFilter" />
      <MobileFileList
        :items="displayItems"
        :loading="loading"
        :selecting="selectedCount > 0"
        :selected-ids="selectedIds"
        empty-text="当前目录暂无文件"
        @item-click="onMobileItemClick"
        @item-long-press="onMobileItemLongPress"
        @item-toggle="onMobileItemToggle"
        @item-more="onMobileItemMore"
      />
      <div class="m-mp-pagination">
        <button class="m-mp-page-btn m-tap" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
        <span class="m-mp-page-info">第 {{ page }} / {{ totalPages || 1 }} 页 · 共 {{ total }} 项</span>
        <button class="m-mp-page-btn m-tap" type="button" :disabled="page >= totalPages || loading || totalPages === 0" @click="changePage(page + 1)">下一页</button>
      </div>
      <MobileSelectionBar
        :visible="selectedCount > 0"
        :count="selectedCount"
        :actions="mobileSelectionActions"
        :bottom-offset="56"
        @cancel="clearSelection"
        @action="onMobileSelectionAction"
      />
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />


    <div v-if="uploadDialogOpen" class="dialog-mask" @click.self="closeUploadDialog">
      <div class="dialog-panel upload-dialog" role="dialog" aria-modal="true" aria-labelledby="upload-dialog-title">
        <div class="dialog-head">
          <h2 id="upload-dialog-title">上传文件</h2>
          <p>上方选择文件，下方也可以直接拖入文件上传。</p>
        </div>
        <label class="primary-button upload-dialog-trigger" :class="{ disabled: submitting || uploadProcessing }">
          <AppIcon name="upload" :size="16" />
          <span>{{ uploadProcessing ? '处理中...' : '选择文件上传' }}</span>
          <input ref="uploadInputRef" type="file" multiple :disabled="submitting || uploadProcessing" @change="onUploadDialogFiles" />
        </label>
        <div
          class="upload-dialog-dropzone"
          :class="{ active: dragActive }"
          @dragenter.prevent="dragActive = true"
          @dragover.prevent="dragActive = true"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDropFiles"
        >
          <AppIcon name="upload" :size="18" />
          <div class="upload-dialog-copy">
            <strong>拖入文件上传</strong>
            <span>支持多文件队列、失败重试和同名冲突处理</span>
          </div>
        </div>
        <div v-if="uploadQueue.length" class="upload-queue upload-dialog-queue">
          <div class="upload-queue-head">
            <strong>上传队列</strong>
            <span>{{ uploadSummary }}</span>
          </div>
          <div class="upload-queue-list">
            <div v-for="entry in uploadQueue" :key="entry.id" class="upload-queue-item">
              <div class="upload-queue-copy">
                <strong>{{ entry.file.name }}</strong>
                <span>{{ describeUploadStatus(entry) }}</span>
              </div>
              <div class="upload-queue-actions">
                <button
                  v-if="entry.status === 'failed'"
                  class="ghost-button queue-action"
                  type="button"
                  @click="retryUpload(entry)"
                >
                  重试
                </button>
                <button
                  v-if="entry.status !== 'uploading'"
                  class="ghost-button queue-action"
                  type="button"
                  @click="removeUploadEntry(entry.id)"
                >
                  移除
                </button>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" @click="closeUploadDialog">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="shareDialogOpen" class="dialog-mask" @click.self="closeShareDialog">
      <div
        v-if="shareDialogMode === 'main'"
        class="dialog-panel share-dialog share-dialog-main"
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-dialog-title"
        @click="closeShareMenus"
      >
        <div class="share-dialog-top">
          <div class="share-back-title">
            <h2 id="share-dialog-title">分享</h2>
          </div>
          <button class="share-close-button" type="button" @click="closeShareDialog">
            <AppIcon name="close" :size="18" />
          </button>
        </div>

        <section class="share-overview-card">
          <div class="share-overview-copy">
            <span class="share-overview-kicker">协作</span>
            <div class="share-overview-row">
              <div class="share-overview-icon">
                <AppIcon name="share" :size="18" />
              </div>
              <div class="share-overview-text">
                <span class="share-overview-title">和他人一起查看 / 编辑</span>
                <span>{{ shareDialogSummary }}</span>
              </div>
            </div>
          </div>
          <button class="share-switch" :class="{ active: shareCollaborationEnabled }" type="button" disabled aria-label="共享状态">
            <span class="share-switch-thumb" />
          </button>
        </section>

        <section class="share-main-card">
          <div class="share-main-row">
            <div class="share-main-copy">
              <span class="share-section-title">已共享协作者</span>
              <span>{{ shareCollaboratorSummaryText }}</span>
            </div>
            <button class="ghost-button share-inline-action" type="button" :disabled="shareSummaryLoading || !shareCanManageCollaborators" @click="openManageCollaborators">
              管理协作者
            </button>
          </div>
          <div class="share-collaborator-preview">
            <div class="share-owner-row">
              <span class="share-avatar">{{ shareOwnerInitial }}</span>
              <div class="share-owner-copy">
                <span class="share-owner-name">{{ shareOwnerName }}</span>
                <span class="share-owner-role">文件创建者</span>
              </div>
            </div>

            <div v-if="sharePreviewCollaborators.length" class="share-preview-list">
              <div
                v-for="collaborator in sharePreviewCollaborators"
                :key="collaborator.shareId"
                class="share-preview-item"
              >
                <span class="share-avatar secondary">{{ collaborator.targetName?.slice(0, 1) || '?' }}</span>
                <div class="share-preview-copy">
                  <span class="share-preview-name">{{ collaborator.targetName }}</span>
                  <span>{{ formatShareTargetTypeLabel(collaborator.targetType) }} · {{ formatSharePermissionLabel(collaborator.permission) }}</span>
                </div>
              </div>
            </div>
            <div v-else class="share-preview-empty">
              <span>暂未添加协作者</span>
            </div>
          </div>
        </section>

        <div class="share-main-actions">
          <button class="ghost-button" type="button" @click="openAddCollaborators">添加协作者</button>
          <button class="primary-button" type="button" :disabled="shareSummaryLoading" @click="openAddCollaborators">更新协作人员</button>
        </div>
      </div>

      <div
        v-else-if="shareDialogMode === 'add'"
        class="dialog-panel share-dialog share-dialog-wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-add-dialog-title"
        @click="closeShareMenus"
      >
        <div class="share-dialog-top">
          <div class="share-back-title">
            <button class="share-back-button" type="button" @click="goShareMainView">
              <AppIcon name="chevron-left" :size="18" />
            </button>
            <h2 id="share-add-dialog-title">添加协作者</h2>
          </div>
          <button class="share-close-button" type="button" @click="closeShareDialog">
            <AppIcon name="close" :size="18" />
          </button>
        </div>

        <div class="share-add-layout">
          <section class="share-search-column">
            <label class="dialog-field">
              <span>搜索协作者</span>
              <input v-model.trim="shareKeyword" type="text" :placeholder="shareSearchPlaceholder" />
            </label>

            <div class="share-filter-chips">
              <button
                v-for="option in shareTargetTypeOptions"
                :key="option.key"
                class="share-filter-chip"
                :class="{ active: shareTargetType === option.key }"
                type="button"
                @click="onChangeShareTargetType(option.key)"
              >
                {{ option.label }}
              </button>
            </div>

            <div class="share-search-results">
              <div class="share-panel-head">
                <strong>搜索结果</strong>
                <span v-if="shareSearchLoading">搜索中...</span>
              </div>
              <div class="share-result-list">
                <button
                  v-for="target in shareSearchResults"
                  :key="`${target.type}:${target.id}`"
                  class="share-result-item"
                  type="button"
                  :disabled="isShareTargetAlreadyListed(target)"
                  @click="addShareTarget(target)"
                >
                  <span class="share-result-name">{{ target.name }}</span>
                  <span class="share-result-meta">{{ formatShareSearchMeta(target) }}</span>
                </button>
                <div v-if="shareSearchEmptyState" class="share-empty-state">
                  <span class="share-empty-text">{{ shareSearchEmptyState }}</span>
                  <button
                    v-if="showShareSyncEntry"
                    class="ghost-button share-sync-entry"
                    type="button"
                    @click="goToAdminSync"
                  >
                    前往系统台同步数据
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section class="share-selected-panel">
            <div class="share-panel-head">
              <strong>待添加对象</strong>
              <span>{{ shareSelectedDisplayTargets.length }} 个</span>
            </div>
            <div class="share-selected-list">
              <div
                v-for="target in shareSelectedDisplayTargets"
                :key="`${target.type}:${target.id}`"
                class="share-selected-chip"
                :class="{ 'is-existing': target.existing }"
              >
                <div class="share-selected-copy">
                  <strong>{{ target.name }}</strong>
                </div>
                <button
                  type="button"
                  class="share-chip-remove"
                  @click="removeShareTarget(target)"
                >
                  移除
                </button>
              </div>
              <span v-if="!shareSelectedDisplayTargets.length" class="share-empty-text">未选择共享对象</span>
            </div>

            <div class="share-settings-inline">
              <label class="share-inline-field">
                <span>权限</span>
                <div class="share-choice" @click.stop>
                  <button class="share-choice-trigger" type="button" @click="toggleShareMenu('permission')">
                    <span>{{ formatSharePermissionLabel(sharePermission) }}</span>
                    <AppIcon :name="shareMenuOpen === 'permission' ? 'caret-up' : 'caret-down'" class="share-choice-caret" :size="12" />
                  </button>
                  <div v-if="shareMenuOpen === 'permission'" class="share-choice-menu">
                    <button
                      v-for="option in sharePermissionOptions"
                      :key="option.value"
                      class="share-choice-item"
                      :class="{ active: sharePermission === option.value }"
                      type="button"
                      @click="sharePermission = option.value; closeShareMenus()"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>
              </label>
              <label class="share-inline-field">
                <span>有效期</span>
                <div class="share-choice" @click.stop>
                  <button class="share-choice-trigger" type="button" @click="toggleShareMenu('expiry')">
                    <span>{{ formatShareExpiryLabel(shareExpiryMode) }}</span>
                    <AppIcon :name="shareMenuOpen === 'expiry' ? 'caret-up' : 'caret-down'" class="share-choice-caret" :size="12" />
                  </button>
                  <div v-if="shareMenuOpen === 'expiry'" class="share-choice-menu">
                    <button
                      v-for="option in shareExpiryOptions"
                      :key="option.value"
                      class="share-choice-item"
                      :class="{ active: shareExpiryMode === option.value }"
                      type="button"
                      @click="shareExpiryMode = option.value; if (option.value !== 'date') { shareExpiryDate = '' } closeShareMenus()"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>
              </label>
              <label v-if="shareExpiryMode === 'date'" class="share-inline-field share-inline-field-wide">
                <span>到期日期</span>
                <input v-model="shareExpiryDate" type="date" />
              </label>
            </div>
          </section>
        </div>

        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="goShareMainView">返回</button>
          <button
            class="primary-button"
            type="button"
            :disabled="submitting || !shareItems.length || !shareSelectedTargets.length || (shareExpiryMode === 'date' && !shareExpiryDate)"
            @click="onConfirmShare"
          >
            更新协作人员
          </button>
        </div>
      </div>

      <div
        v-else
        class="dialog-panel share-dialog share-dialog-manage"
        role="dialog"
        aria-modal="true"
        aria-labelledby="share-manage-dialog-title"
        @click="closeShareMenus"
      >
        <div class="share-dialog-top">
          <div class="share-back-title">
            <button class="share-back-button" type="button" @click="goShareMainView">
              <AppIcon name="chevron-left" :size="18" />
            </button>
            <h2 id="share-manage-dialog-title">管理协作者</h2>
          </div>
          <button class="share-close-button" type="button" @click="closeShareDialog">
            <AppIcon name="close" :size="18" />
          </button>
        </div>

        <label class="dialog-field">
          <span>搜索协作者</span>
          <input v-model.trim="shareManageKeyword" type="text" placeholder="搜索协作者" />
        </label>

        <div class="share-manage-list">
          <div class="share-manage-owner">
            <span class="share-avatar">{{ shareOwnerInitial }}</span>
            <div class="share-manage-copy">
              <strong>{{ shareOwnerName }}</strong>
              <span>文件创建者</span>
            </div>
          </div>

          <div
            v-for="collaborator in filteredManageCollaborators"
            :key="collaborator.shareId"
            class="share-manage-item"
          >
            <span class="share-avatar secondary">{{ collaborator.targetName?.slice(0, 1) || '?' }}</span>
            <div class="share-manage-copy">
              <strong>{{ collaborator.targetName }}</strong>
              <span>{{ formatShareTargetTypeLabel(collaborator.targetType) }} · {{ formatSharePermissionLabel(collaborator.permission) }}<template v-if="collaborator.expiresAt"> · {{ collaborator.expiresAt }}</template></span>
            </div>
            <div class="share-manage-actions">
              <button class="ghost-button share-inline-action" type="button" :disabled="submitting" @click="openEditCollaborator(collaborator)">编辑</button>
              <button class="ghost-button share-inline-action danger" type="button" :disabled="submitting" @click="onRevokeCollaborator(collaborator)">取消共享</button>
            </div>
          </div>

          <div v-if="!filteredManageCollaborators.length" class="share-preview-empty">
            <span>未查询到信息。</span>
          </div>
        </div>

        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="goShareMainView">返回</button>
          <button class="primary-button" type="button" :disabled="submitting" @click="openAddCollaborators">添加协作者</button>
        </div>
      </div>
    </div>

    <div v-if="shareEditDialogOpen" class="dialog-mask" @click.self="closeShareEditDialog">
      <div class="dialog-panel share-dialog-edit" role="dialog" aria-modal="true" aria-labelledby="share-edit-dialog-title" @click="closeShareMenus">
        <div class="dialog-head">
          <h2 id="share-edit-dialog-title">管理协作者</h2>
          <p>{{ shareEditTarget?.targetName || '调整权限与有效期' }}</p>
        </div>
        <div class="share-settings-inline">
          <label class="share-inline-field">
            <span>权限</span>
            <div class="share-choice" @click.stop>
              <button class="share-choice-trigger" type="button" @click="toggleShareMenu('edit-permission')">
                <span>{{ formatSharePermissionLabel(shareEditPermission) }}</span>
                <AppIcon :name="shareMenuOpen === 'edit-permission' ? 'caret-up' : 'caret-down'" class="share-choice-caret" :size="12" />
              </button>
              <div v-if="shareMenuOpen === 'edit-permission'" class="share-choice-menu">
                <button
                  v-for="option in sharePermissionOptions"
                  :key="option.value"
                  class="share-choice-item"
                  :class="{ active: shareEditPermission === option.value }"
                  type="button"
                  @click="shareEditPermission = option.value; closeShareMenus()"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>
          </label>
          <label class="share-inline-field">
            <span>有效期</span>
            <div class="share-choice" @click.stop>
              <button class="share-choice-trigger" type="button" @click="toggleShareMenu('edit-expiry')">
                <span>{{ formatShareExpiryLabel(shareEditExpiryMode) }}</span>
                <AppIcon :name="shareMenuOpen === 'edit-expiry' ? 'caret-up' : 'caret-down'" class="share-choice-caret" :size="12" />
              </button>
              <div v-if="shareMenuOpen === 'edit-expiry'" class="share-choice-menu">
                <button
                  v-for="option in shareExpiryOptions"
                  :key="option.value"
                  class="share-choice-item"
                  :class="{ active: shareEditExpiryMode === option.value }"
                  type="button"
                  @click="shareEditExpiryMode = option.value; if (option.value !== 'date') { shareEditExpiryDate = '' } closeShareMenus()"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>
          </label>
          <label v-if="shareEditExpiryMode === 'date'" class="share-inline-field share-inline-field-wide">
            <span>到期日期</span>
            <input v-model="shareEditExpiryDate" type="date" />
          </label>
        </div>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeShareEditDialog">取消</button>
          <button class="primary-button" type="button" :disabled="submitting || (shareEditExpiryMode === 'date' && !shareEditExpiryDate)" @click="onConfirmShareEdit">
            保存
          </button>
        </div>
      </div>
    </div>

    <div v-if="false && shareDialogOpen" class="dialog-mask" @click.self="closeShareDialog">
      <div class="dialog-panel share-dialog" role="dialog" aria-modal="true" aria-labelledby="share-dialog-title">
        <div class="dialog-head">
          <h2 id="share-dialog-title">共享文件</h2>
          <p>{{ shareDialogSummary }}</p>
        </div>

        <div class="share-target-type-group">
          <button
            v-for="option in shareTargetTypeOptions"
            :key="option.key"
            class="workspace-filter"
            :class="{ active: shareTargetType === option.key }"
            type="button"
            @click="onChangeShareTargetType(option.key)"
          >
            {{ option.label }}
          </button>
        </div>

        <label class="dialog-field">
          <span>搜索共享对象</span>
          <input v-model.trim="shareKeyword" type="text" :placeholder="shareSearchPlaceholder" />
        </label>

        <div class="share-search-panel">
          <div class="share-search-results">
            <div class="share-panel-head">
              <strong>搜索结果</strong>
              <span v-if="shareSearchLoading">搜索中...</span>
            </div>
            <div class="share-result-list">
              <button
                v-for="target in shareSearchResults"
                :key="`${target.type}:${target.id}`"
                class="share-result-item"
                type="button"
                @click="addShareTarget(target)"
              >
                <span class="share-result-name">{{ target.name }}</span>
                <span class="share-result-meta">{{ target.distinguishedName || target.id }}</span>
              </button>
              <div v-if="shareSearchEmptyState" class="share-empty-state">
                <span class="share-empty-text">{{ shareSearchEmptyState }}</span>
                <button
                  v-if="showShareSyncEntry"
                  class="ghost-button share-sync-entry"
                  type="button"
                  @click="goToAdminSync"
                >
                  前往系统台同步数据
                </button>
              </div>
            </div>
          </div>

          <div class="share-selected-panel">
            <div class="share-panel-head">
              <strong>已选对象</strong>
              <span>{{ shareSelectedTargets.length }} 个</span>
            </div>
            <div class="share-selected-list">
              <div
                v-for="target in shareSelectedTargets"
                :key="`${target.type}:${target.id}`"
                class="share-selected-chip"
              >
                <div class="share-selected-copy">
                  <strong>{{ target.name }}</strong>
                  <span>{{ target.distinguishedName || target.id }}</span>
                </div>
                <button type="button" class="share-chip-remove" @click="removeShareTarget(target)">移除</button>
              </div>
              <span v-if="!shareSelectedTargets.length" class="share-empty-text">尚未选择共享对象</span>
            </div>
          </div>
        </div>

        <div class="share-settings-grid">
          <label class="dialog-field">
            <span>权限</span>
            <select v-model="sharePermission">
              <option value="read">只读</option>
              <option value="download">可下载</option>
              <option value="edit">可编辑</option>
            </select>
          </label>
          <label class="dialog-field">
            <span>有效期</span>
            <select v-model="shareExpiryMode">
              <option value="forever">长期有效</option>
              <option value="date">指定日期</option>
            </select>
          </label>
          <label v-if="shareExpiryMode === 'date'" class="dialog-field">
            <span>到期日期</span>
            <input v-model="shareExpiryDate" type="date" />
          </label>
        </div>

        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeShareDialog">取消</button>
          <button
            class="primary-button"
            type="button"
            :disabled="submitting || !shareItems.length || !shareSelectedTargets.length || (shareExpiryMode === 'date' && !shareExpiryDate)"
            @click="onConfirmShare"
          >
            确认共享
          </button>
        </div>
      </div>
    </div>

    <div v-if="renameDialogOpen" class="dialog-mask" @click.self="closeRenameDialog">
      <div class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="rename-dialog-title">
        <div class="dialog-head">
          <h2 id="rename-dialog-title">重命名</h2>
          <p>{{ renameTarget?.itemType === 'folder' ? '请输入新的文件夹名称。' : '请输入新的文件名称。' }}</p>
        </div>
        <label class="dialog-field">
          <span>名称</span>
          <input
            ref="renameInputRef"
            v-model.trim="renameDraft"
            type="text"
            maxlength="255"
            @keydown.enter.prevent="onConfirmRename"
            @keydown.esc.prevent="closeRenameDialog"
          />
        </label>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeRenameDialog">取消</button>
          <button class="primary-button" type="button" :disabled="submitting || !renameDraft.trim()" @click="onConfirmRename">
            确认
          </button>
        </div>
      </div>
    </div>

    <div v-if="moveDialogOpen" class="dialog-mask" @click.self="closeMoveDialog">
      <div class="dialog-panel move-dialog" role="dialog" aria-modal="true" aria-labelledby="move-dialog-title">
        <div class="dialog-head">
          <h2 id="move-dialog-title">移动到</h2>
          <p>目标目录：{{ moveDialogPathText }}</p>
        </div>
        <div class="move-toolbar">
          <button class="ghost-button" type="button" @click="moveBrowseToRoot">根目录</button>
          <button class="ghost-button" type="button" :disabled="!moveBrowseParentId" @click="moveBrowseToParent">上一级</button>
          <label class="toolbar-select compact">
            <span>冲突</span>
            <select v-model="moveConflictStrategy">
              <option value="abort">遇到同名则停止</option>
              <option value="keep_both">保留两份</option>
              <option value="replace">覆盖目标项</option>
            </select>
          </label>
        </div>
        <div class="move-folder-list">
          <button
            v-for="folder in moveFolders"
            :key="folder.id"
            class="move-folder-item"
            type="button"
            @click="moveBrowseInto(folder)"
          >
            <span class="move-folder-icon"><AppIcon name="folder" :size="16" /></span>
            <span>{{ folder.name }}</span>
          </button>
          <span v-if="!moveFolders.length" class="move-folder-empty">当前目录下没有子文件夹</span>
        </div>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeMoveDialog">取消</button>
          <button class="primary-button" type="button" :disabled="submitting || moveLoading" @click="onConfirmMove">
            移动 {{ moveDialogItems.length }} 项
          </button>
        </div>
      </div>
    </div>

    <div v-if="batchRenameDialogOpen" class="dialog-mask" @click.self="closeBatchRenameDialog">
      <div class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="batch-rename-title">
        <div class="dialog-head">
          <h2 id="batch-rename-title">批量重命名</h2>
          <p>按当前列表顺序生成新名称，文件会保留原扩展名。</p>
        </div>
        <div class="batch-rename-grid">
          <label class="dialog-field">
            <span>基础名</span>
            <input v-model.trim="batchRenameDraft.baseName" type="text" maxlength="120" />
          </label>
          <label class="dialog-field">
            <span>起始序号</span>
            <input v-model.number="batchRenameDraft.startNumber" type="number" min="0" />
          </label>
          <label class="dialog-field">
            <span>位数</span>
            <input v-model.number="batchRenameDraft.numberWidth" type="number" min="1" max="6" />
          </label>
        </div>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeBatchRenameDialog">取消</button>
          <button class="primary-button" type="button" :disabled="submitting || !batchRenameDraft.baseName.trim()" @click="onConfirmBatchRename">
            应用到 {{ selectedCount }} 项
          </button>
        </div>
      </div>
    </div>
    <DriveCopyDialog
      :open="copyDialogOpen"
      :items="copyDialogItems"
      @close="closeCopyDialog"
      @copied="onCopyCompleted"
    />
  </section>
</template>
<script setup>

import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DriveCopyDialog from '../../components/drive/DriveCopyDialog.vue';
import AppIcon from '../../components/icons/AppIcon.vue';
import {
  batchDownloadDriveItems,
  batchRenameDriveItems,
  createFolder,
  createDriveShares,
  deleteDriveItem,
  downloadDriveItem,
  editDriveItem,
  getDriveShareSummary,
  getSharedAdminConfig,
  moveDriveItems,
  previewDriveItem,
  renameDriveItem,
  revokeDriveShare,
  searchOrgTargets,
  updateDriveShare
} from '../../api/cloudnext';
import {
  CLOUDNEXT_DRIVE_REFRESH_EVENT,
  CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT,
  emitDriveRefreshSignal
} from '../../session';
import { useDriveDirectoryState } from '../../composables/drive/useDriveDirectoryState';
import { useDriveMoveBrowser } from '../../composables/drive/useDriveMoveBrowser';
import { useDriveSelection } from '../../composables/drive/useDriveSelection';
import { useDriveUploadQueue } from '../../composables/drive/useDriveUploadQueue';
import MobileFilterChips from '../../components/mobile/MobileFilterChips.vue';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import MobileSelectionBar from '../../components/mobile/MobileSelectionBar.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const route = useRoute();
const router = useRouter();

const submitting = ref(false);
const renameDialogOpen = ref(false);
const renameTarget = ref(null);
const renameDraft = ref('');
const renameInputRef = ref(null);
const batchRenameDialogOpen = ref(false);
const batchRenameDraft = ref({
  baseName: '',
  startNumber: 1,
  numberWidth: 2
});
const shareDialogOpen = ref(false);
const shareItems = ref([]);
const shareTargetType = ref('person');
const shareKeyword = ref('');
const shareSearchLoading = ref(false);
const shareSearchResults = ref([]);
const shareSelectedTargets = ref([]);
const sharePermission = ref('read');
const shareExpiryMode = ref('forever');
const shareExpiryDate = ref('');
const shareDialogMode = ref('main');
const shareSummaryLoading = ref(false);
const shareSummary = ref(null);
const shareManageKeyword = ref('');
const shareEditDialogOpen = ref(false);
const shareEditTarget = ref(null);
const shareEditPermission = ref('read');
const shareEditExpiryMode = ref('forever');
const shareEditExpiryDate = ref('');
const shareMenuOpen = ref('');
const orgMirrorReady = ref(false);
const orgMirrorEmpty = ref(false);
const maxUploadFileSizeBytes = ref(0);
const maxUploadFileSizeLabel = ref('');

const copyDialogOpen = ref(false);
const copyDialogItems = ref([]);
let shareSearchTimer = null;
let pendingFabTimer = null;

const filters = [
  { key: 'all', label: '全部' },
  { key: 'folder', label: '文件夹' },
  { key: 'file', label: '文件' }
];

const shareTargetTypeOptions = [
  { key: 'person', label: '人员' },
  { key: 'unit', label: '部门' },
  { key: 'identity', label: '身份' }
];

const sharePermissionOptions = [
  { value: 'read', label: '只读' },
  { value: 'download', label: '可下载' },
  { value: 'edit', label: '可编辑' }
];

const shareExpiryOptions = [
  { value: 'forever', label: '长期有效' },
  { value: 'date', label: '指定日期' }
];

const {
  activeFilter,
  sortBy,
  sortOrder,
  toggleSort,
  setSort,
  page,
  pageSize,
  loading,
  total,
  totalPages,
  breadcrumbs,
  currentParentId,
  displayItems,
  refreshCurrentDirectory,
  applyFilter,
  changePage,
  goToFolder,
  goToRoot,
  onOpenFolder
} = useDriveDirectoryState({
  route,
  router
});

const {
  selectedIds,
  selectedItems,
  selectedCount,
  selectedFileItems,
  clearSelection,
  onSelectionChange
} = useDriveSelection(displayItems);

const {
  moveDialogOpen,
  moveDialogItems,
  moveBrowseId,
  moveFolders,
  moveLoading,
  moveConflictStrategy,
  moveBrowseParentId,
  moveDialogPathText,
  openMoveDialog: openMoveBrowserDialog,
  moveBrowseInto: browseMoveFolder,
  moveBrowseToRoot: browseMoveRoot,
  moveBrowseToParent: browseMoveParent,
  closeMoveDialog: closeMoveBrowserDialog
} = useDriveMoveBrowser();

const {
  uploadDialogOpen,
  uploadProcessing,
  dragActive,
  uploadQueue,
  uploadInputRef,
  uploadSummary,
  openUploadDialog,
  closeUploadDialog,
  onUploadDialogFiles,
  removeUploadEntry,
  retryUpload,
  describeUploadStatus,
  onDragLeave,
  onDropFiles
} = useDriveUploadQueue({
  currentParentId,
  getMaxUploadFileSizeBytes: () => maxUploadFileSizeBytes.value,
  getMaxUploadFileSizeLabel: () => maxUploadFileSizeLabel.value,
  onQueueCompleted: async () => {
    await refreshDirectory();
    emitDriveRefreshSignal();
  }
});

const loadAdminConfig = async () => {
  try {
    const config = await getSharedAdminConfig();
    maxUploadFileSizeBytes.value = Number(config?.maxUploadFileSizeBytes || 0);
    maxUploadFileSizeLabel.value = config?.maxUploadFileSize || '';
    orgMirrorReady.value = Boolean(config?.orgMirrorReady);
    orgMirrorEmpty.value = Boolean(config?.orgMirrorEmpty);
  } catch (e) {
  }
};

const refreshDirectory = async () => {
  await refreshCurrentDirectory({ preferredBreadcrumbs: breadcrumbs.value });
};

const refreshAfterMutation = async () => {
  await refreshDirectory();
  emitDriveRefreshSignal();
};

const isSingleShareItem = computed(() => shareItems.value.length === 1);

const primaryShareItem = computed(() => (isSingleShareItem.value ? shareItems.value[0] : null));

const shareDialogSummary = computed(() => {
  if (!shareItems.value.length) {
    return '选择协作者并设置共享权限。';
  }
  if (primaryShareItem.value) {
    return `当前共享项：${primaryShareItem.value.name}`;
  }
  return `当前将共享 ${shareItems.value.length} 个项目`;
});

const shareSearchPlaceholder = computed(() => {
  if (shareTargetType.value === 'unit') {
    return '搜索部门名称';
  }
  if (shareTargetType.value === 'identity') {
    return '搜索身份名称';
  }
  return '搜索人员名称';
});

const shareSearchEmptyState = computed(() => {
  if (shareSearchLoading.value || shareSearchResults.value.length) {
    return '';
  }
  if (!shareKeyword.value.trim()) {
    return '输入关键词后可搜索共享对象';
  }
  return '未查询到信息。';
});

const showShareSyncEntry = computed(() => shareDialogOpen.value && shareDialogMode.value === 'add' && orgMirrorEmpty.value);

const shareCollaborationEnabled = computed(() => Boolean(isSingleShareItem.value && shareSummary.value?.shared));

const shareOwnerName = computed(() => shareSummary.value?.ownerName || primaryShareItem.value?.ownerName || '当前用户');

const shareOwnerInitial = computed(() => {
  const name = String(shareOwnerName.value || '').trim();
  return name ? name.slice(0, 1).toUpperCase() : '?';
});

const shareCollaborators = computed(() => {
  const collaborators = shareSummary.value?.collaborators;
  return Array.isArray(collaborators) ? collaborators : [];
});

const extractShareUid = (value) => {
  const text = String(value || '').trim();
  if (!text) {
    return '';
  }
  const parts = text.split('@').filter(Boolean);
  if (parts.length >= 3) {
    return parts.slice(1, -1).join('@') || parts[1] || text;
  }
  if (parts.length === 2) {
    return parts[1];
  }
  return text;
};

const formatShareSearchMeta = (target) => {
  if (!target) {
    return '';
  }
  if (target.type === 'unit') {
    return target.path || target.distinguishedName || target.id || '';
  }
  return extractShareUid(target.distinguishedName) || target.id || '';
};

const shareExistingTargets = computed(() => {
  if (!isSingleShareItem.value) {
    return [];
  }
  return shareCollaborators.value.map((collaborator) => ({
    shareId: collaborator.shareId,
    type: collaborator.targetType,
    id: collaborator.targetId,
    name: collaborator.targetName,
    existing: true
  }));
});

const shareSelectedDisplayTargets = computed(() => {
  const seen = new Set();
  const merged = [];
  for (const target of [...shareExistingTargets.value, ...shareSelectedTargets.value]) {
    const key = `${target.type}:${target.id}`;
    if (!seen.has(key)) {
      seen.add(key);
      merged.push(target);
    }
  }
  return merged;
});

const sharePreviewCollaborators = computed(() => shareCollaborators.value.slice(0, 3));

const shareCollaboratorSummaryText = computed(() => {
  if (!isSingleShareItem.value) {
    return '多项共享将在添加协作者后统一生效';
  }
  const activeCount = Number(shareSummary.value?.activeTargetCount || 0);
  if (!activeCount) {
    return '暂未共享给其他协作者';
  }
  return `已共享给 ${activeCount} 位协作者`;
});

const shareCanManageCollaborators = computed(() => isSingleShareItem.value);

const isShareTargetAlreadyListed = (target) => shareSelectedDisplayTargets.value.some((item) => item.type === target.type && item.id === target.id);

const filteredManageCollaborators = computed(() => {
  const keyword = shareManageKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return shareCollaborators.value;
  }
  return shareCollaborators.value.filter((collaborator) => {
    const haystack = [
      collaborator.targetName,
      collaborator.targetType,
      collaborator.permission,
      collaborator.expiresAt
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(keyword);
  });
});

const formatSharePermissionLabel = (permission) => {
  if (permission === 'download') {
    return '可下载';
  }
  if (permission === 'edit') {
    return '可编辑';
  }
  return '只读';
};

const formatShareTargetTypeLabel = (targetType) => {
  if (targetType === 'unit') {
    return '部门';
  }
  if (targetType === 'identity') {
    return '身份';
  }
  return '人员';
};

const formatShareExpiryLabel = (value) => {
  if (value === 'date') {
    return '指定日期';
  }
  return '长期有效';
};

const toggleShareMenu = (key) => {
  shareMenuOpen.value = shareMenuOpen.value === key ? '' : key;
};

const closeShareMenus = () => {
  shareMenuOpen.value = '';
};

const loadShareSummary = async () => {
  if (!primaryShareItem.value) {
    shareSummary.value = null;
    return;
  }
  shareSummaryLoading.value = true;
  try {
    shareSummary.value = await getDriveShareSummary(primaryShareItem.value.id);
  } catch (e) {
    shareSummary.value = null;
  } finally {
    shareSummaryLoading.value = false;
  }
};

const resetShareDialogState = () => {
  shareItems.value = [];
  shareTargetType.value = 'person';
  shareKeyword.value = '';
  shareSearchLoading.value = false;
  shareSearchResults.value = [];
  shareSelectedTargets.value = [];
  sharePermission.value = 'read';
  shareExpiryMode.value = 'forever';
  shareExpiryDate.value = '';
  shareDialogMode.value = 'main';
  shareSummary.value = null;
  shareSummaryLoading.value = false;
  shareManageKeyword.value = '';
  shareEditDialogOpen.value = false;
  shareEditTarget.value = null;
  shareEditPermission.value = 'read';
  shareEditExpiryMode.value = 'forever';
  shareEditExpiryDate.value = '';
  shareMenuOpen.value = '';
};

const closeShareEditDialog = () => {
  shareEditDialogOpen.value = false;
  shareEditTarget.value = null;
  shareEditPermission.value = 'read';
  shareEditExpiryMode.value = 'forever';
  shareEditExpiryDate.value = '';
  shareMenuOpen.value = '';
};

const closeShareDialog = (force = false) => {
  if (submitting.value && !force) {
    return;
  }
  shareDialogOpen.value = false;
  window.clearTimeout(shareSearchTimer);
  closeShareEditDialog();
  resetShareDialogState();
};

const addShareTarget = (target) => {
  if (!target?.id || !target?.type) {
    return;
  }
  const exists = shareSelectedDisplayTargets.value.some((item) => item.type === target.type && item.id === target.id);
  if (!exists) {
    shareSelectedTargets.value = [...shareSelectedTargets.value, target];
  }
};

const removeShareTarget = async (target) => {
  if (target?.existing && target.shareId) {
    await onRevokeCollaborator(target);
    return;
  }
  shareSelectedTargets.value = shareSelectedTargets.value.filter((item) => !(item.type === target.type && item.id === target.id));
};

const runShareSearch = async () => {
  const keyword = shareKeyword.value.trim();
  if (!shareDialogOpen.value || shareDialogMode.value !== 'add' || !keyword) {
    shareSearchLoading.value = false;
    shareSearchResults.value = [];
    return;
  }
  shareSearchLoading.value = true;
  try {
    const results = await searchOrgTargets(shareTargetType.value, keyword);
    shareSearchResults.value = Array.isArray(results) ? results : [];
  } catch (e) {
    shareSearchResults.value = [];
  } finally {
    shareSearchLoading.value = false;
  }
};

const onChangeShareTargetType = (type) => {
  shareTargetType.value = type;
  shareSearchResults.value = [];
};

const goShareMainView = async () => {
  shareDialogMode.value = 'main';
  shareKeyword.value = '';
  shareSearchResults.value = [];
  shareManageKeyword.value = '';
  closeShareEditDialog();
  if (isSingleShareItem.value) {
    await loadShareSummary();
  }
};

const openAddCollaborators = () => {
  shareDialogMode.value = 'add';
  shareKeyword.value = '';
  shareSearchResults.value = [];
  closeShareEditDialog();
};

const openManageCollaborators = async () => {
  if (!shareCanManageCollaborators.value) {
    return;
  }
  shareManageKeyword.value = '';
  closeShareEditDialog();
  await loadShareSummary();
  shareDialogMode.value = 'manage';
};

const onOpenShareDialog = (items) => {
  const normalized = Array.isArray(items) ? items.filter((item) => item?.id) : [];
  if (!normalized.length) {
    return;
  }
  void loadAdminConfig();
  resetShareDialogState();
  shareItems.value = normalized;
  shareDialogOpen.value = true;
  if (normalized.length === 1) {
    void loadShareSummary();
  }
};

const onShareOne = (item) => {
  onOpenShareDialog([item]);
};

const onConfirmShare = async () => {
  if (!shareItems.value.length || !shareSelectedTargets.value.length) {
    return;
  }
  submitting.value = true;
  try {
    await createDriveShares({
      itemIds: shareItems.value.map((item) => item.id),
      targets: shareSelectedTargets.value.map((target) => ({
        type: target.type,
        id: target.id,
        name: target.name
      })),
      permission: sharePermission.value,
      expiresAt: shareExpiryMode.value === 'date' ? shareExpiryDate.value : ''
    });
    emitDriveRefreshSignal();
    if (isSingleShareItem.value) {
      shareSelectedTargets.value = [];
      shareKeyword.value = '';
      shareSearchResults.value = [];
      await loadShareSummary();
      shareDialogMode.value = 'main';
    } else {
      closeShareDialog(true);
      clearSelection();
    }
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
};

const openEditCollaborator = (collaborator) => {
  if (!collaborator?.shareId) {
    return;
  }
  shareEditTarget.value = collaborator;
  shareEditPermission.value = collaborator.permission || 'read';
  shareEditExpiryMode.value = collaborator.expiresAt ? 'date' : 'forever';
  shareEditExpiryDate.value = collaborator.expiresAt || '';
  shareEditDialogOpen.value = true;
};

const onConfirmShareEdit = async () => {
  if (!shareEditTarget.value?.shareId) {
    return;
  }
  submitting.value = true;
  try {
    await updateDriveShare(shareEditTarget.value.shareId, {
      permission: shareEditPermission.value,
      expiresAt: shareEditExpiryMode.value === 'date' ? shareEditExpiryDate.value : ''
    });
    closeShareEditDialog();
    await loadShareSummary();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
};

const onRevokeCollaborator = async (collaborator) => {
  if (!collaborator?.shareId) {
    return;
  }
  if (!window.confirm(`确认取消与“${collaborator.targetName}”的共享吗？`)) {
    return;
  }
  submitting.value = true;
  try {
    await revokeDriveShare(collaborator.shareId);
    await loadShareSummary();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
};

const goToAdminSync = () => {
  closeShareDialog(true);
  router.push({ name: 'admin' }).catch(() => {});
};

const onCreateFolder = async () => {
  const name = window.prompt('请输入文件夹名称');
  if (!name || !name.trim()) {
    return;
  }
  submitting.value = true;
  try {
    await createFolder({ name: name.trim(), parentId: currentParentId.value });
    await refreshAfterMutation();
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const onPreview = async (item) => {
  submitting.value = true;
  try {
    await previewDriveItem(item);
    emitDriveRefreshSignal();
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const onEdit = async (item) => {
  submitting.value = true;
  try {
    await editDriveItem(item);
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const onBatchPreview = async () => {
  if (!selectedFileItems.value.length) {
    return;
  }
  await onPreview(selectedFileItems.value[0]);
};

const onDownload = async (item) => {
  submitting.value = true;
  try {
    await downloadDriveItem(item);
    emitDriveRefreshSignal();
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const deleteItems = async (targetItems) => {
  if (!targetItems.length) {
    return;
  }
  const confirmText =
    targetItems.length === 1
      ? `确认将“${targetItems[0].name}”移入回收站吗？`
      : `确认将选中的 ${targetItems.length} 项移入回收站吗？`;
  if (!window.confirm(confirmText)) {
    return;
  }
  submitting.value = true;
  try {
    await Promise.all(targetItems.map((item) => deleteDriveItem(item.id)));
    await refreshDirectory();
    clearSelection();
    emitDriveRefreshSignal();
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const onDelete = async (item) => {
  await deleteItems([item]);
};

const onBatchDelete = async () => {
  await deleteItems(selectedItems.value);
};

const onBatchDownload = async () => {
  if (!selectedFileItems.value.length) {
    return;
  }
  submitting.value = true;
  try {
    for (const item of selectedFileItems.value) {
      await downloadDriveItem(item);
    }
    emitDriveRefreshSignal();
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};



const onSelectionMoreAction = (action) => {
  if (action === 'share') {
    onOpenShareDialog(selectedItems.value);
    return;
  }
  if (action === 'preview') {
    void onBatchPreview();
    return;
  }
  if (action === 'move') {
    void onOpenMoveDialog(selectedItems.value);
    return;
  }
  if (action === 'copy') {
    openCopyDialog(selectedItems.value);
    return;
  }
  if (action === 'zip-download') {
    void onZipDownload();
  }
};

const zipDownloading = ref(false);

const onZipDownload = async () => {
  if (zipDownloading.value) {
    return;
  }
  const ids = selectedItems.value.map((it) => it.id).filter(Boolean);
  if (!ids.length) {
    return;
  }
  zipDownloading.value = true;
  try {
    const folderName = breadcrumbs.value?.[breadcrumbs.value.length - 1]?.name;
    const zipName = (folderName || '我的文件') + '-' + ids.length + '项';
    await batchDownloadDriveItems(ids, zipName);
  } catch (e) {
    window.alert(e?.message || '打包下载失败');
  } finally {
    zipDownloading.value = false;
  }
};

const onStartRename = async (item) => {
  renameTarget.value = item;
  renameDraft.value = item.name || '';
  renameDialogOpen.value = true;
  await nextTick();
  renameInputRef.value?.focus?.();
  renameInputRef.value?.select?.();
};

const closeRenameDialog = (force = false) => {
  if (submitting.value && !force) {
    return;
  }
  renameDialogOpen.value = false;
  renameTarget.value = null;
  renameDraft.value = '';
};

const onConfirmRename = async () => {
  if (!renameTarget.value || !renameDraft.value.trim()) {
    return;
  }
  submitting.value = true;
  try {
    await renameDriveItem(renameTarget.value.id, { name: renameDraft.value.trim() });
    await refreshAfterMutation();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
  closeRenameDialog(true);
};

const onMoveOne = (item) => {
  void onOpenMoveDialog([item]);
};

const openCopyDialog = (items) => {
  copyDialogItems.value = Array.isArray(items) ? items.filter(Boolean) : [];
  copyDialogOpen.value = copyDialogItems.value.length > 0;
};

const onCopyOne = (item) => openCopyDialog([item]);
const closeCopyDialog = () => {
  copyDialogOpen.value = false;
  copyDialogItems.value = [];
};
const onCopyCompleted = async () => {
  closeCopyDialog();
  clearSelection();
  await refreshDirectory();
  emitDriveRefreshSignal();
};

const onOpenMoveDialog = async (targetItems) => {
  try {
    await openMoveBrowserDialog(targetItems);
  } catch (e) {
    closeMoveDialog(true);
  }
};

const moveBrowseInto = async (folder) => {
  try {
    await browseMoveFolder(folder);
  } catch (e) {
  }
};

const moveBrowseToRoot = async () => {
  try {
    await browseMoveRoot();
  } catch (e) {
  }
};

const moveBrowseToParent = async () => {
  try {
    await browseMoveParent();
  } catch (e) {
  }
};

const closeMoveDialog = (force = false) => {
  if (submitting.value && !force) {
    return;
  }
  closeMoveBrowserDialog();
};

const onConfirmMove = async () => {
  if (!moveDialogItems.value.length) {
    return;
  }
  submitting.value = true;
  try {
    await moveDriveItems({
      itemIds: moveDialogItems.value.map((item) => item.id),
      targetParentId: moveBrowseId.value,
      conflictStrategy: moveConflictStrategy.value
    });
    await refreshDirectory();
    clearSelection();
    emitDriveRefreshSignal();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
  closeMoveDialog(true);
};

const onOpenBatchRenameDialog = () => {
  if (!selectedCount.value) {
    return;
  }
  batchRenameDraft.value = {
    baseName: '',
    startNumber: 1,
    numberWidth: 2
  };
  batchRenameDialogOpen.value = true;
};

const closeBatchRenameDialog = (force = false) => {
  if (submitting.value && !force) {
    return;
  }
  batchRenameDialogOpen.value = false;
};

const onConfirmBatchRename = async () => {
  submitting.value = true;
  try {
    const result = await batchRenameDriveItems({
      itemIds: selectedItems.value.map((item) => item.id),
      baseName: batchRenameDraft.value.baseName.trim(),
      startNumber: batchRenameDraft.value.startNumber,
      numberWidth: batchRenameDraft.value.numberWidth
    });
    if (result?.totalFailed) {
      throw new Error(result.failedItems?.[Object.keys(result.failedItems)[0]] || result.message || '批量重命名失败');
    }
    await refreshAfterMutation();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
  closeBatchRenameDialog(true);
};

const onDriveRefresh = () => {
  void refreshDirectory().catch(() => {});
};

const onOpenUploadModal = () => {
  openUploadDialog();
};

// === 移动端逻辑 ===

const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');

const mobileCurrentFolderName = computed(() => {
  const crumbs = breadcrumbs.value || [];
  return crumbs[crumbs.length - 1]?.name || '我的文件';
});

const mobileSheetActions = computed(() => {
  const item = mobileSheetItem.value;
  if (!item) {
    return [];
  }
  if (item.itemType === 'folder') {
    return [
      { key: 'open', label: '打开' },
      { key: 'share', label: '共享' },
      { key: 'rename', label: '重命名' },
      { key: 'move', label: '移动到' },
      { key: 'copy', label: '复制到' },
      { key: 'delete', label: '删除', danger: true }
    ];
  }
  return [
    { key: 'preview', label: '预览' },
    { key: 'edit', label: '在线编辑' },
    { key: 'download', label: '下载' },
    { key: 'share', label: '共享' },
    { key: 'rename', label: '重命名' },
    { key: 'move', label: '移动到' },
    { key: 'copy', label: '复制到' },
    { key: 'delete', label: '删除', danger: true }
  ];
});

const MOBILE_SEL_ICON_DOWNLOAD = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>';
const MOBILE_SEL_ICON_SHARE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>';
const MOBILE_SEL_ICON_MOVE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="5 9 2 12 5 15"/><polyline points="9 5 12 2 15 5"/><polyline points="15 19 12 22 9 19"/><polyline points="19 9 22 12 19 15"/><line x1="2" y1="12" x2="22" y2="12"/><line x1="12" y1="2" x2="12" y2="22"/></svg>';
const MOBILE_SEL_ICON_COPY = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>';
const MOBILE_SEL_ICON_RENAME = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>';
const MOBILE_SEL_ICON_DELETE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>';

const mobileSelectionActions = computed(() => [
  {
    key: 'download',
    label: zipDownloading.value ? '打包中…' : (selectedItems.value.length > 1 || selectedItems.value.some((it) => it.itemType === 'folder') ? '打包下载' : '下载'),
    icon: MOBILE_SEL_ICON_DOWNLOAD,
    disabled: !selectedCount.value || zipDownloading.value
  },
  { key: 'share', label: '共享', icon: MOBILE_SEL_ICON_SHARE },
  { key: 'move', label: '移动', icon: MOBILE_SEL_ICON_MOVE },
  { key: 'copy', label: '复制', icon: MOBILE_SEL_ICON_COPY },
  { key: 'rename', label: '改名', icon: MOBILE_SEL_ICON_RENAME },
  { key: 'delete', label: '删除', icon: MOBILE_SEL_ICON_DELETE, danger: true }
]);

const onMobileGoBack = () => {
  const crumbs = breadcrumbs.value || [];
  if (crumbs.length <= 1) {
    goToRoot();
  } else {
    goToFolder(crumbs[crumbs.length - 2].id);
  }
};

const onMobileItemClick = (item) => {
  if (selectedCount.value > 0) {
    onMobileItemToggle(item);
    return;
  }
  if (item.itemType === 'folder') {
    onOpenFolder(item);
  } else {
    void onPreview(item);
  }
};

const onMobileItemLongPress = (item) => {
  if (!selectedIds.value.includes(item.id)) {
    selectedIds.value = [...selectedIds.value, item.id];
  }
};

const onMobileItemToggle = (item) => {
  const next = new Set(selectedIds.value);
  if (next.has(item.id)) {
    next.delete(item.id);
  } else {
    next.add(item.id);
  }
  selectedIds.value = Array.from(next);
};

const onMobileItemMore = (item) => {
  mobileSheetItem.value = item;
  mobileSheetOpen.value = true;
};

const onMobileSheetSelect = (key) => {
  const item = mobileSheetItem.value;
  mobileSheetOpen.value = false;
  if (!item) {
    return;
  }
  switch (key) {
    case 'open':
      onOpenFolder(item);
      break;
    case 'preview':
      void onPreview(item);
      break;
    case 'edit':
      void onEdit(item);
      break;
    case 'download':
      void onDownload(item);
      break;
    case 'share':
      onShareOne(item);
      break;
    case 'rename':
      void onStartRename(item);
      break;
    case 'move':
      onMoveOne(item);
      break;
    case 'copy':
      onCopyOne(item);
      break;
    case 'delete':
      void onDelete(item);
      break;
  }
};

const onMobileSelectionAction = (key) => {
  switch (key) {
    case 'download':
      if (selectedItems.value.length === 1 && selectedItems.value[0]?.itemType === 'file') {
        void onBatchDownload();
      } else {
        void onZipDownload();
      }
      break;
    case 'share':
      onSelectionMoreAction('share');
      break;
    case 'move':
      onSelectionMoreAction('move');
      break;
    case 'copy':
      onSelectionMoreAction('copy');
      break;
    case 'rename':
      onOpenBatchRenameDialog();
      break;
    case 'delete':
      void onBatchDelete();
      break;
  }
};

const onMobileFabAction = (event) => {

  const key = event?.detail?.key;
  if (!key) {
    return;
  }
  if (key === 'folder') {
    void onCreateFolder();
  } else if (key === 'upload' || key === 'photo') {
    openUploadDialog();
  }
};

watch([shareKeyword, shareTargetType, shareDialogOpen], () => {
  window.clearTimeout(shareSearchTimer);
  if (!shareDialogOpen.value || shareDialogMode.value !== 'add' || !shareKeyword.value.trim()) {
    shareSearchLoading.value = false;
    shareSearchResults.value = [];
    return;
  }
  shareSearchTimer = window.setTimeout(() => {
    void runShareSearch();
  }, 220);
});

watch(currentParentId, () => {
  clearSelection();
  closeUploadDialog();
  closeShareDialog(true);
  closeRenameDialog(true);
  closeMoveDialog(true);
  closeBatchRenameDialog(true);
});

onMounted(() => {
  void loadAdminConfig();
  window.addEventListener(CLOUDNEXT_DRIVE_REFRESH_EVENT, onDriveRefresh);
  window.addEventListener(CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT, onOpenUploadModal);
  window.addEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
  // 从 HomePage FAB 跳转过来时回放 pending action
  try {
    const pending = sessionStorage.getItem('cloudnext-pending-fab');
    if (pending) {
      sessionStorage.removeItem('cloudnext-pending-fab');
      pendingFabTimer = window.setTimeout(() => {
        pendingFabTimer = null;
        window.dispatchEvent(new CustomEvent('cloudnext:mobile-fab-action', { detail: { key: pending } }));
      }, 30);
    }
  } catch (e) {
    /* ignore storage errors */
  }
});

onUnmounted(() => {
  window.removeEventListener(CLOUDNEXT_DRIVE_REFRESH_EVENT, onDriveRefresh);
  window.removeEventListener(CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT, onOpenUploadModal);
  window.removeEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
  window.clearTimeout(shareSearchTimer);
  window.clearTimeout(pendingFabTimer);
  pendingFabTimer = null;
});
</script>
<style scoped>

.breadcrumb-text,
.breadcrumb-current {
  display: inline-flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 18px;
  line-height: 1.4;
  min-height: 40px;
}

.breadcrumb-text {
  padding: 0;
  background: transparent;
}

.breadcrumb-text:hover,
.breadcrumb-text.active,
.breadcrumb-current {
  color: var(--text-primary);
}

.breadcrumb-current {
  font-weight: 600;
}

.breadcrumb-divider {
  display: inline-flex;
  align-items: center;
  color: var(--text-tertiary);
  font-size: 12px;
  min-height: 40px;
}

.upload-dialog-dropzone {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
  transition: border-color 0.18s ease, background 0.18s ease;
}

.upload-dialog-dropzone.active {
  border-color: rgba(47, 107, 255, 0.45);
  background: var(--bg-primary-soft);
  color: var(--text-primary);
}

.upload-dialog-copy {
  display: grid;
  gap: 2px;
}

.upload-dialog-copy strong {
  color: var(--text-primary);
  font-size: 14px;
}

.upload-dialog-copy span {
  font-size: 12px;
}

.upload-queue {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
}

.upload-queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.upload-queue-head strong {
  font-size: 14px;
}

.upload-queue-head span,
.upload-queue-copy span {
  color: var(--text-secondary);
  font-size: 12px;
}

.upload-queue-list {
  display: grid;
  gap: 8px;
}

.upload-queue-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
}

.upload-queue-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.upload-queue-copy strong {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-queue-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.queue-action {
  min-height: 30px;
  padding: 0 10px;
}

.toolbar-select {
  display: grid;
  gap: 6px;
}

.toolbar-select span {
  font-size: 12px;
  color: var(--text-secondary);
}

.toolbar-select select {
  min-width: 120px;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 12px;
  background: var(--bg-panel);
  color: var(--text-primary);
}

.toolbar-select.compact select {
  min-width: 96px;
}

.drive-directory-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.18);
}

.dialog-panel {
  width: min(520px, 100%);
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.upload-dialog {
  width: min(560px, 100%);
}

.move-dialog {
  width: min(640px, 100%);
}

.upload-dialog-trigger {
  position: relative;
  overflow: hidden;
  width: fit-content;
}

.upload-dialog-trigger input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload-dialog-trigger.disabled {
  pointer-events: none;
  opacity: 0.72;
}

.upload-dialog-queue {
  max-height: 280px;
  overflow: auto;
}

.dialog-head {
  display: grid;
  gap: 6px;
}

.dialog-head h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.dialog-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.dialog-field {
  display: grid;
  gap: 8px;
}

.dialog-field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.dialog-field input,
.dialog-field select {
  width: 100%;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 14px;
  color: var(--text-primary);
  background: var(--bg-panel);
}

.dialog-field input:focus,
.dialog-field select:focus {
  border-color: rgba(47, 107, 255, 0.6);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.12);
}

.move-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.move-folder-list {
  display: grid;
  gap: 8px;
  max-height: 260px;
  overflow: auto;
}

.move-folder-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
  color: var(--text-primary);
}

.move-folder-item:hover {
  background: var(--bg-primary-soft);
}

.move-folder-icon {
  display: inline-flex;
  color: #d97706;
}

.move-folder-empty {
  color: var(--text-secondary);
  font-size: 13px;
}

.batch-rename-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.share-dialog {
  width: min(760px, 100%);
}

.share-dialog-main {
  width: min(500px, 100%);
}

.share-dialog-wide {
  width: min(720px, 100%);
}

.share-dialog-manage {
  width: min(400px, 100%);
}

.share-dialog-edit {
  width: min(460px, 100%);
}

.share-dialog-top,
.share-back-title,
.share-main-row,
.share-main-actions,
.share-owner-row,
.share-preview-item,
.share-manage-owner,
.share-manage-item,
.share-manage-actions {
  display: flex;
  align-items: center;
}

.share-dialog-top,
.share-main-row,
.share-main-actions,
.share-manage-item {
  justify-content: space-between;
}

.share-dialog-top {
  gap: 12px;
}

.share-back-title {
  gap: 10px;
  min-width: 0;
}

.share-back-title h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.share-close-button,
.share-back-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  color: var(--text-secondary);
}

.share-close-button:hover,
.share-back-button:hover {
  background: var(--bg-panel-soft);
  color: var(--text-primary);
}

.share-overview-card,
.share-main-card,
.share-manage-list {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-panel);
}

.share-overview-card {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
}

.share-overview-copy {
  display: grid;
  gap: 10px;
}

.share-overview-kicker {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.share-overview-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.share-overview-icon,
.share-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 999px;
}

.share-overview-icon {
  width: 36px;
  height: 36px;
  background: rgba(47, 107, 255, 0.1);
  color: #2f6bff;
}

.share-overview-text,
.share-main-copy,
.share-owner-copy,
.share-preview-copy,
.share-manage-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  justify-items: start;
  text-align: left;
}

.share-dialog-main .share-overview-kicker,
.share-dialog-main .share-inline-action,
.share-dialog-main .ghost-button,
.share-dialog-main .primary-button,
.share-dialog-main .share-overview-title,
.share-dialog-main .share-section-title,
.share-dialog-main .share-preview-name,
.share-dialog-main .share-owner-name,
.share-dialog-main .share-owner-role,
.share-dialog-main .share-overview-text span,
.share-dialog-main .share-main-copy span,
.share-dialog-main .share-preview-copy span,
.share-dialog-main .share-preview-empty span {
  font-weight: 400;
}

.share-dialog-wide .dialog-field span,
.share-dialog-wide .share-filter-chip,
.share-dialog-wide .share-empty-text,
.share-dialog-wide .share-selected-copy strong,
.share-dialog-wide .share-selected-copy span,
.share-dialog-wide .share-panel-head strong,
.share-dialog-wide .share-panel-head span,
.share-dialog-wide .ghost-button,
.share-dialog-wide .primary-button,
.share-dialog-manage .dialog-field span,
.share-dialog-manage .share-manage-copy strong,
.share-dialog-manage .share-manage-copy span,
.share-dialog-manage .share-inline-action,
.share-dialog-manage .secondary-button,
.share-dialog-manage .primary-button,
.share-dialog-edit .dialog-head p,
.share-dialog-edit .share-inline-field span,
.share-dialog-edit .share-choice-trigger,
.share-dialog-edit .secondary-button,
.share-dialog-edit .primary-button {
  font-weight: 400;
}

.share-overview-text strong,
.share-main-copy strong,
.share-owner-copy strong,
.share-preview-copy strong,
.share-manage-copy strong {
  font-size: 14px;
  color: var(--text-primary);
}

.share-overview-text span,
.share-main-copy span,
.share-owner-copy span,
.share-preview-copy span,
.share-manage-copy span,
.share-preview-empty span {
  font-size: 12px;
  color: var(--text-secondary);
}

.share-switch {
  position: relative;
  width: 48px;
  height: 28px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.35);
  opacity: 1;
}

.share-switch.active {
  background: #2f6bff;
}

.share-switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #fff;
  transition: transform 0.18s ease;
}

.share-switch.active .share-switch-thumb {
  transform: translateX(20px);
}

.share-inline-action {
  min-height: 32px;
  padding: 0 12px;
}

.share-inline-action.danger {
  color: #b42318;
}

.share-inline-action.danger:hover:not(:disabled) {
  background: var(--bg-danger-soft);
}

.share-collaborator-preview,
.share-preview-list {
  display: grid;
  gap: 10px;
}

.share-avatar {
  width: 34px;
  height: 34px;
  background: rgba(47, 107, 255, 0.14);
  color: #2f6bff;
  font-size: 13px;
  font-weight: 700;
}

.share-dialog-main .share-avatar {
  width: 32px;
  height: 32px;
}

.share-avatar.secondary {
  width: 30px;
  height: 30px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-primary);
}

.share-preview-item,
.share-owner-row,
.share-manage-owner {
  gap: 10px;
}

.share-dialog-main .share-owner-row {
  align-items: center;
}

.share-dialog-main .share-owner-copy {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.share-dialog-main .share-preview-copy {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.share-dialog-main .share-owner-name,
.share-dialog-main .share-owner-role,
.share-dialog-main .share-preview-name {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
}

.share-dialog-main .share-owner-role,
.share-dialog-main .share-preview-copy > span:last-child {
  color: var(--text-secondary);
}

.share-preview-empty {
  display: grid;
  justify-items: start;
}

.share-main-actions {
  gap: 10px;
}

.share-main-actions .ghost-button,
.share-main-actions .primary-button {
  min-height: 40px;
  padding-inline: 16px;
}

.share-add-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.88fr);
  gap: 14px;
}

.share-search-column {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.share-filter-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.share-filter-chip {
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--border-strong);
  background: var(--bg-panel);
  color: var(--text-secondary);
  font-size: 13px;
  transition: color 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}

.share-filter-chip.active {
  border-color: rgba(47, 107, 255, 0.28);
  background: rgba(47, 107, 255, 0.08);
  color: #2f6bff;
}

.share-search-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.share-search-results,
.share-selected-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  align-content: start;
  gap: 10px;
  min-height: 240px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
}

.share-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  align-self: start;
}

.share-panel-head strong {
  font-size: 14px;
  color: var(--text-primary);
}

.share-panel-head span,
.share-result-meta,
.share-selected-copy span,
.share-empty-text {
  font-size: 12px;
  color: var(--text-secondary);
}

.share-result-list,
.share-selected-list {
  display: grid;
  gap: 2px;
  align-content: start;
  min-height: 0;
  max-height: 260px;
  overflow: auto;
}

.share-selected-panel .share-settings-grid {
  margin-top: 4px;
}

.share-empty-state {
  display: grid;
  align-content: start;
  justify-items: start;
  gap: 10px;
}

.share-sync-entry {
  min-height: 32px;
  padding: 0 10px;
}

.share-result-item,
.share-selected-chip {
  display: grid;
  gap: 2px;
  padding: 6px 8px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
}

.share-result-item {
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.share-result-item:hover {
  border-color: rgba(47, 107, 255, 0.28);
  background: var(--bg-primary-soft);
}

.share-result-item:disabled {
  cursor: default;
  opacity: 0.56;
  border-color: var(--border-color);
  background: var(--bg-panel-soft);
}

.share-result-name,
.share-selected-copy strong {
  font-size: 13px;
  color: var(--text-primary);
}

.share-selected-chip {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.share-selected-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.share-chip-remove {
  color: #b42318;
  font-size: 12px;
}

.share-settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.share-settings-inline {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.share-inline-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.share-inline-field span {
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-primary);
}

.share-inline-field select,
.share-inline-field input {
  flex: 1 1 auto;
  min-width: 0;
  height: auto;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  text-align: right;
  color: var(--text-primary);
}

.share-choice {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
}

.share-choice-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  width: 100%;
  min-height: 28px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
  color: var(--text-primary);
  font-size: 14px;
}

.share-choice-caret {
  color: var(--text-secondary);
  flex: 0 0 auto;
}

.share-choice-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 8;
  min-width: 124px;
  display: grid;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.share-choice-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 34px;
  padding: 0 12px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  text-align: left;
}

.share-choice-item:hover,
.share-choice-item.active {
  background: var(--bg-primary-soft);
  color: #2f6bff;
}

.share-inline-field select:focus,
.share-inline-field input:focus {
  border: 0;
  box-shadow: none;
  outline: none;
}

.share-inline-field-wide {
  grid-column: 1 / -1;
}

.share-manage-list {
  max-height: 420px;
  overflow: auto;
}

.share-manage-actions {
  gap: 8px;
  flex-wrap: wrap;
}

.share-manage-item {
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color);
}

.share-preview-copy,
.share-manage-copy {
  flex: 1 1 auto;
}

.share-manage-item:first-of-type {
  border-top: 0;
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1080px) {
  .dialog-actions {
    align-items: stretch;
  }

  .dialog-actions {
    width: 100%;
  }

  .dialog-actions button {
    flex: 1;
  }

  .batch-rename-grid {
    grid-template-columns: 1fr;
  }

  .share-overview-card,
.share-add-layout,
.share-search-panel,
.share-settings-inline,
.share-settings-grid {
    grid-template-columns: 1fr;
  }

  .share-main-actions {
    flex-direction: column;
  }
}

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-mp-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  height: 40px;
  padding: 0 var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
  font-size: var(--m-fs-body);
  color: var(--text-secondary);
}

.m-mp-back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 32px;
  padding: 0 var(--m-sp-2);
  background: transparent;
  color: var(--bg-primary);
  border-radius: var(--radius-md);
}

.m-mp-back:active {
  background: var(--bg-nav-hover);
}

.m-mp-current {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-weight: 500;
}

.m-mp-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: #fff;
  border-top: 1px solid rgba(15, 23, 42, 0.04);
  font-size: var(--m-fs-meta);
  color: var(--text-tertiary);
}

.m-mp-page-btn {
  height: 36px;
  padding: 0 var(--m-sp-3);
  background: var(--bg-panel-soft);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  font-size: var(--m-fs-meta);
}

.m-mp-page-btn:active:not(:disabled) {
  background: var(--bg-nav-hover);
}

.m-mp-page-btn:disabled {
  opacity: 0.4;
}

.m-mp-page-info {
  flex: 1;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (max-width: 559px) {
  .dialog-mask {
    padding: 12px;
    align-items: center;
  }

  .dialog-panel {
    width: 100%;
    max-width: 100%;
    padding: 16px;
    gap: 12px;
    max-height: 90vh;
    overflow-y: auto;
  }

  .dialog-head h2 {
    font-size: 17px;
  }

  .dialog-head p {
    font-size: 12px;
  }

  .dialog-field {
    gap: 6px;
  }

  .dialog-field input,
.dialog-field select,
.dialog-field textarea {
    height: 40px;
    font-size: 14px;
  }

  .dialog-actions {
    flex-direction: column-reverse;
    gap: 8px;
  }

  .dialog-actions > button {
    width: 100%;
    min-height: 44px;
  }

  .upload-dialog,
.move-dialog,
.share-dialog-main,
.share-dialog-wide,
.share-dialog-manage,
.share-dialog-edit {
    width: 100%;
    max-width: 100%;
  }

  .share-add-layout {
    grid-template-columns: 1fr;
  }

  .upload-dialog-queue {
    max-height: 200px;
  }
  .share-overview-card,
.share-main-card,
.share-manage-list {
    padding: 12px;
    gap: 10px;
  }

  .share-overview-card {
    gap: 12px;
  }

  .share-overview-copy {
    gap: 6px;
  }

  .share-main-actions {
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 8px;
  }

  .share-main-actions > button {
    flex: 1 1 0;
    min-width: 0;
    min-height: 40px;
    padding-inline: 10px;
    white-space: nowrap;
  }
}

</style>
