button group - icon only

```jsx
<div>
  <Button className="raised-low" icon="angle-up" groupPosition="left" />
  <Button className="raised-low" icon="angle-up" groupPosition="middle" />
  <Button className="raised-low" icon="angle-up" groupPosition="right" />
</div>
```

Clickable (check console)

```jsx
<Button
  className="raised-low"
  icon="save"
  onClick={() => console.log("Button Clicked")}
/>
```

Small icon
```jsx
<Button
  className="raised-low"
  size='small'
  icon="save"
/>
```

Medium (default) icon
```jsx
<Button
  className="raised-low"
  size='medium'
  icon="save"
/>
```

Large icon
```jsx
<Button
  className="raised-low"
  size='large'  
  icon="save"
/>
```

Extra large icon
```jsx
<Button
  className="raised-low"
  size='xlarge'  
  icon="save"
/>
```

button group - icon and text

```jsx
<div>
  <Button
    className="raised-low"
    icon="angle-up"
    text="Button 1"
    groupPosition="left"
  />
  <Button
    className="raised-low"
    icon="angle-up"
    text="Button 2"
    groupPosition="middle"
  />
  <Button
    className="raised-low"
    icon="angle-up"
    text="Button 3"
    groupPosition="right"
  />
</div>
```

button group - text only

```jsx
<div>
  <Button className="raised-low" text="Button 1" groupPosition="left" />
  <Button className="raised-low" text="Button 2" groupPosition="middle" />
  <Button className="raised-low" text="Button 3" groupPosition="right" />
</div>
```

icon and text

```jsx
<Button className="raised-low" icon="angle-up" text="Button text" />
```

just text

```jsx
<Button className="raised-low" text="Button text" />
```

just icon

```jsx
<Button className="raised-low" icon="trash" />
```

circular icon

```jsx
<Button className="raised-low" circular icon="trash" />
```

circular icon and text - should be weird

```jsx
<Button className="raised-low" circular icon="trash" text="Madness" />
```

selected - icon and text

```jsx
<Button className="raised-low" selected icon="angle-up" text="Button text" />
```

disabled

```jsx
<div>
  <Button className="raised-low" disabled icon="trash" />
  <Button className="raised-low" disabled circular icon="trash" />
  <Button className="raised-low" disabled text="Button text" />
  <Button
    className="raised-low"
    disabled
    selected
    icon="angle-up"
    text="Button text"
  />
</div>
```

many buttons

```jsx
<div>
  <Button className="raised-low" text="Button 1" groupPosition="left" />
  <Button className="raised-low" text="Button 2" groupPosition="middle" />
  <Button className="raised-low" text="Button 3" groupPosition="right" />

  <Button className="raised-low" circular icon="trash" />

  <Button
    className="raised-low"
    selected
    icon="angle-up"
    text="A selected button"
  />

  <Button className="raised-low" icon="angle-up" groupPosition="left" />
  <Button className="raised-low" icon="angle-up" groupPosition="middle" />
  <Button className="raised-low" icon="angle-up" groupPosition="right" />

  <Button className="raised-low" icon="angle-up" groupPosition="left" />
  <Button className="raised-low" icon="angle-up" groupPosition="middle" />
  <Button className="raised-low" icon="angle-up" groupPosition="right" />

  <Button className="raised-low" icon="trash" />

  <Button className="raised-low" icon="trash" />
</div>
```
